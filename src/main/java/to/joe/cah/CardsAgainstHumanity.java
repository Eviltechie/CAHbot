package to.joe.cah;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.jibble.pircbot.Colors;
import org.jibble.pircbot.PircBot;

public class CardsAgainstHumanity extends PircBot {

    // TODO remember score after leaving
    // TODO don't let people spam join/leave
    // TODO Shortcuts

    // TODO Inactivity timeout
    // TODO Check scores in game
    // TODO some sort of bug with !cah drop and causing another pick
    // TODO Hold drop's until after card is picked, should fix above bug
    // TODO Opportunity to dump hand after 10 rounds
    // TODO HOF
    // TODO Delay on remove from game on disconnect

    enum GameStatus {
        Idle, // No game is playing
        WaitingForPlayers, // 30 second period where players should join
        WaitingForCards, // Waiting for all players to play cards
        ChoosingWinner
        // Waiting for the czar to pick a winner
    }

    // \x03#,# \u0003 Colors
    // \x02 \u0002 Bold

    public static void main(String[] args) throws Exception {
        CardsAgainstHumanity bot = new CardsAgainstHumanity("CAHBot");
        bot.setVerbose(true);
        bot.connect("irc.gamesurge.net");
        bot.joinChannel(gameChannel);
        bot.setMessageDelay(2300);
    }

    final static String gameChannel = "#joe.to";
    private ArrayList<Player> currentPlayers = new ArrayList<Player>();
    private HashSet<Player> allPlayers = new HashSet<Player>();
    // private ArrayList<Player> blacklist = new ArrayList<Player>();
    private ArrayList<Player> currentShuffledPlayers;
    private ArrayList<String> originalBlackCards = new ArrayList<String>();
    private ArrayList<String> activeBlackCards = new ArrayList<String>();
    private ArrayList<String> originalWhiteCards = new ArrayList<String>();
    private ArrayList<String> activeWhiteCards = new ArrayList<String>();
    private String currentBlackCard;
    private Timer timer = new Timer();
    private GameStatus currentGameStatus = GameStatus.Idle;
    private Player currentCzar;
    public int requiredAnswers = 1;

    public CardsAgainstHumanity(String botName) throws Exception {
        this.setName(botName);
        File blackFile = new File("black.txt");
        File whiteFile = new File("white.txt");
        this.ifNotExists(blackFile, whiteFile);

        FileReader fileReader = new FileReader(blackFile);
        BufferedReader bufferedReader = new BufferedReader(fileReader);
        String line;
        while ((line = bufferedReader.readLine()) != null) {
            originalBlackCards.add(line);
        }
        fileReader.close();
        bufferedReader.close();

        fileReader = new FileReader("white.txt");
        bufferedReader = new BufferedReader(fileReader);
        while ((line = bufferedReader.readLine()) != null) {
            originalWhiteCards.add(line);
        }
        fileReader.close();
        bufferedReader.close();
    }

    public void checkForPlayedCards() {
        int playedCardsCount = 0;
        for (Player player : currentPlayers) {
            if (player.playedCard != null)
                playedCardsCount++;
        }
        if (playedCardsCount + 1 == currentPlayers.size()) {
            this.message("All players have played their white cards");
            this.message("The black card is " + Colors.BOLD + "\"" + currentBlackCard + "\"" + Colors.NORMAL + " The white cards are:");
            playedCardsCount = 0;
            currentShuffledPlayers = new ArrayList<Player>(currentPlayers);
            currentShuffledPlayers.remove(currentCzar);
            Collections.shuffle(currentShuffledPlayers);
            for (Player player : currentShuffledPlayers) {
                playedCardsCount++;
                this.message(playedCardsCount + ") " + player.playedCard);
            }
            this.message(currentCzar.getName() + ": Pick the best white card");
            currentGameStatus = GameStatus.ChoosingWinner;
        }
    }
    
    public void message(String message) {
        this.sendMessage(gameChannel, message);
    }

    private void drop(String name) {
        /*
         * if (status == GameStatus.Idle) { this.sendMessage(channel,
         * "There is no game currently playing to drop yourself from"); return;
         * }
         */
        Player player = getPlayer(name);
        if(player == null){
            return;
        }
        this.message(player.getName() + " has left this game of Cards Against Humanity with " + player.getScore() + " points!");
        currentPlayers.remove(player);
        // blacklist.add(p);
        if(currentPlayers.size() < 3)
            stop();
        if (currentCzar.equals(player))
            newCzar();
        else
            checkForPlayedCards();
    }

    public String getOrdinal(int value) {
        int hundredRemainder = value % 100;
        if (hundredRemainder >= 10 && hundredRemainder <= 20) {
            return "th";
        }
        int tenRemainder = value % 10;
        switch (tenRemainder) {
        case 1:
            return "st";
        case 2:
            return "nd";
        case 3:
            return "rd";
        default:
            return "th";
        }
    }

    private Player getPlayer(String name) {
        for (Player player : currentPlayers) {
            if (player.getName().equals(name))
                return player;
        }
        return null;
    }

    private void ifNotExists(File... files) {
        for (File file : files) {
            if (file.exists()) {
                continue;
            }
            System.out.println("Saving " + file);
            InputStream inputStream = CardsAgainstHumanity.class.getClassLoader().getResourceAsStream(file.getName());
            try {
                file.createNewFile();
                FileOutputStream outputStream = new FileOutputStream(file);
                byte buffer[] = new byte[1024];
                int length;
                while ((length = inputStream.read(buffer)) > 0) {
                    outputStream.write(buffer, 0, length);
                }
                outputStream.close();
                inputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void join(String name) {
        if (currentGameStatus == GameStatus.Idle) {
            this.message("There is no game currently playing. Try starting one with !cah start");
            return;
        }
        for (Player player : currentPlayers) {
            if (player.getName().equals(name)) {
                this.message(name + ": you can't join this game twice!");
                return;
            }
        }
        /*
         * for (Player p : blacklist) { if (p.getName().equals(name)) {
         * this.message(name +
         * ": You can't join a game after leaving one"); return; } }
         */
        for (Player player : allPlayers) {
            if (player.getName().equalsIgnoreCase(name)) {
                currentPlayers.add(player);
                this.message(Colors.BOLD + name + " rejoins this game of Cards Against Humanity!");
                return;
            }
        }
        currentPlayers.add(new Player(name, this));
        this.message(Colors.BOLD + name + " joins this game of Cards Against Humanity!");
    }

    private void nag(String sender) {
        if (currentGameStatus == GameStatus.WaitingForCards) {
            String missingPlayers = "";
            for (Player player : currentPlayers) {
                if (!player.equals(currentCzar) && player.playedCard == null) {
                    System.out.println(player.getName());
                    missingPlayers += player.getName() + " ";
                }
            }
            this.message("Waiting for " + missingPlayers + "to submit cards");
        } else if (currentGameStatus == GameStatus.ChoosingWinner) {
            this.message("Waiting for " + currentCzar.getName() + " to pick the winning card");
        }
    }

    private void newCzar() {
        Player oldCzar = currentCzar;
        ArrayList<Player> contestants = new ArrayList<Player>(currentPlayers);
        contestants.remove(oldCzar);
        System.out.println("Contestants for czar: " + contestants.toString());
        Collections.shuffle(contestants);
        currentCzar = contestants.get(0);
        this.message(currentCzar.getName() + " is the next czar");
    }

    private void nextTurn() {
        newCzar();
        if (activeBlackCards.size() < 1) {
            activeBlackCards = new ArrayList<String>(originalBlackCards);
            Collections.shuffle(activeBlackCards);
        }
        currentBlackCard = "\u00030,1" + activeBlackCards.remove(0) + "\u0003";
        requiredAnswers = StringUtils.countMatches(currentBlackCard, "_");
        currentBlackCard.replaceAll("_", "<BLANK>");
        this.message("The next black card is " + Colors.BOLD + "\"" + currentBlackCard + "\"");
        if (requiredAnswers > 1)
            message("Be sure to play " + requiredAnswers + " white cards this round");
        currentGameStatus = GameStatus.WaitingForCards;
        for (Player player : currentPlayers) {
            player.playedCard = null;
            player.drawTo10();
            if (!player.equals(currentCzar))
                player.showCardsToPlayer();
        }
    }

    public String nextWhiteCard() {
        if (activeWhiteCards.size() < 1) {
            activeWhiteCards = new ArrayList<String>(originalWhiteCards);
            Collections.shuffle(activeWhiteCards);
        }
        return activeWhiteCards.remove(0);
    }

    @Override
    public void onKick(String channel, String kickerNick, String kickerLogin, String kickerHostname, String recipientNick, String reason) {
        drop(recipientNick);
    }

    @Override
    public void onMessage(String channel, String sender, String login, String hostname, String message) {
        Pattern pattern1 = Pattern.compile("play ((?:[0-9]+ ?){" + requiredAnswers + "})");
        Matcher matcher1 = pattern1.matcher(message);

        Pattern pattern2 = Pattern.compile("pick ([0-9]+)");
        Matcher matcher2 = pattern2.matcher(message);

        Pattern pattern3 = Pattern.compile("!cah boot ([a-zA-Z0-9]+)");
        Matcher matcher3 = pattern3.matcher(message);

        if (!channel.equalsIgnoreCase(CardsAgainstHumanity.gameChannel))
            return;
        else if (message.equalsIgnoreCase("!cah join"))
            join(sender);
        else if (message.equalsIgnoreCase("!cah drop"))
            drop(sender);
        else if (message.equalsIgnoreCase("!cah start") && currentGameStatus == GameStatus.Idle)
            start();
        else if (message.equalsIgnoreCase("!cah stop"))
            stop();
        else if (message.equalsIgnoreCase("cards"))
            getPlayer(sender).showCardsToPlayer();
        else if (matcher1.matches() && currentGameStatus == GameStatus.WaitingForCards && !currentCzar.getName().equals(sender))
            getPlayer(sender).playCard(matcher1.group(1));
        else if (matcher2.matches() && currentGameStatus == GameStatus.ChoosingWinner && currentCzar.getName().equals(sender))
            pickWinner(matcher2.group(1));
        else if (message.equalsIgnoreCase("turn"))
            nag(sender);
        else if (message.equalsIgnoreCase("check"))
            checkForPlayedCards();
        else if (matcher3.matches()) {
            drop(matcher3.group(1));
        }
    }

    @Override
    public void onNickChange(String oldNick, String login, String hostname, String newNick) {
        if (getPlayer(oldNick) != null) {
            getPlayer(oldNick).setName(newNick);
        }
    }

    @Override
    public void onPart(String channel, String sender, String login, String hostname) {
        drop(sender);
    }

    @Override
    public void onQuit(String sourceNick, String sourceLogin, String sourceHostname, String reason) {
        if (!sourceNick.equals(this.getNick()))
            drop(sourceNick);
        else {
            try {
                this.connect("irc.gamesurge.net");
            } catch (Exception e) {
                e.printStackTrace();
            }
            this.joinChannel(gameChannel);
        }
    }

    private void pickWinner(String winningNumber) {
        int cardNumber = 0;
        try {
            cardNumber = Integer.parseInt(winningNumber);
        } catch (NumberFormatException e) {
            this.message(currentCzar.getName() + ": You have picked an invalid card, pick again");
            return;
        }
        Player winningPlayer;
        try {
            winningPlayer = currentShuffledPlayers.get(cardNumber - 1);
        } catch (IndexOutOfBoundsException e) {
            this.message(currentCzar.getName() + ": You have picked an invalid card, pick again");
            return;
        }
        String winningCard = winningPlayer.playedCard;
        this.message("The winning card is " + winningCard + "played by " + Colors.BOLD + winningPlayer.getName() + Colors.NORMAL + ". "
                + Colors.BOLD + winningPlayer.getName() + Colors.NORMAL + " is awarded one point");
        winningPlayer.addPoint();
        nextTurn();
    }

    private void start() {
        activeBlackCards = new ArrayList<String>(originalBlackCards);
        activeWhiteCards = new ArrayList<String>(originalWhiteCards);

        Collections.shuffle(activeBlackCards);
        Collections.shuffle(activeWhiteCards);

        this.message("Game begins in 45 seconds. Type !cah join to join the game.");

        currentGameStatus = GameStatus.WaitingForPlayers;

        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                message("Game starts in 30 seconds");
            }
        }, 15000);

        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                message("Game starts in 15 seconds");
            }
        }, 30000);
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (currentPlayers.size() < 3) {
                    message("Not enough players to start a game");
                    currentPlayers.clear();
                    currentGameStatus = GameStatus.Idle;
                    return;
                }
                // Everything here is pre game stuff
                message("Game starting now!");
                currentCzar = currentPlayers.get(0);
                message(currentCzar.getName() + " is the first czar");
                currentBlackCard = "\u00030,1" + activeBlackCards.remove(0) + "\u0003";
                requiredAnswers = StringUtils.countMatches(currentBlackCard, "_");
                currentBlackCard.replaceAll("_", "<BLANK>");
                message("The first black card is " + Colors.BOLD + "\"" + currentBlackCard + "\"");
                if (requiredAnswers > 1)
                    message("Be sure to play " + requiredAnswers + " white cards this round");
                currentGameStatus = GameStatus.WaitingForCards;
            }
        }, 45000); // 45 seconds
    }

    private void stop() {
        currentGameStatus = GameStatus.Idle;
        currentCzar = null;
        this.message("The game is over!");
        String scoresMessage = "Scores for this game were: ";
        int winningScore = 0;
        for (Player player : allPlayers) {
            scoresMessage += "[" + player.getName() + " " + player.getScore() + "] ";
            if (player.getScore() > winningScore)
                winningScore = player.getScore();
        }
        this.message(scoresMessage);
        scoresMessage = "Winners this game were: ";
        for (Player p : allPlayers) {
            if (p.getScore() == winningScore)
                scoresMessage += p.getName() + " ";
        }
        this.message(scoresMessage);
        allPlayers.clear();
        currentPlayers.clear();
    }
}
