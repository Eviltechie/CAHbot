package to.joe.cah;

import java.util.ArrayList;
import java.util.HashSet;

public class Player {
    private String name;
    private ArrayList<String> whiteCards = new ArrayList<String>();
    private int awesomePoints = 0;
    private CardsAgainstHumanity ircBot;
    public String playedCard = null;

    public Player(String name, CardsAgainstHumanity ircBot) {
        this.name = name;
        this.ircBot = ircBot;
        drawTo10();
        showCardsToPlayer();
    }

    public void addPoint() {
        awesomePoints++;
    }

    public void drawTo10() {
        while (whiteCards.size() < 10) {
            whiteCards.add(ircBot.nextWhiteCard());
        }
    }

    public String getName() {
        return name;
    }

    public int getScore() {
        return awesomePoints;
    }

    @Override
    public int hashCode() {
        return name.toLowerCase().hashCode();
    }

    public void playCard(String cardString) {
        if (playedCard != null) {
            ircBot.message(getName() + ": You can't play twice");
            return;
        }
        String[] cardStrings = cardString.split(" ");
        ArrayList<String> cardsToRemove = new ArrayList<String>();
        playedCard = "";
        for (String s : cardStrings) {
            System.out.println("Attempting to parse " + "\"" + s.replaceAll(" ", "") + "\"");
            int cardNumber = 0;
            try {
                cardNumber = Integer.parseInt(s);
            } catch (NumberFormatException e) {
                ircBot.message(getName() + ": You have picked an invalid card, pick again");
                return;
            }
            cardNumber--;
            try {
                playedCard += "\u00031,0[" + whiteCards.get(cardNumber) + "]\u0003 ";
                cardsToRemove.add(whiteCards.get(cardNumber));
            } catch (IndexOutOfBoundsException e) {
                cardNumber++;
                ircBot.message(getName() + ": You do not appear to have a " + cardNumber + ircBot.getOrdinal(cardNumber)
                        + " card to play");
                playedCard = null;
                return;
            }
        }
        HashSet<String> playedCards = new HashSet<String>(cardsToRemove);
        if (playedCards.size() != cardsToRemove.size()) {
            ircBot.message(getName() + ": You can't play the same card twice");
            return;
        }
        for (String card : cardsToRemove) {
            whiteCards.remove(card);
        }
        if (ircBot.requiredAnswers == 1)
            ircBot.message(getName() + ": Card received");
        else
            ircBot.message(getName() + ": Cards received");
        ircBot.checkForPlayedCards();
    }

    public void setName(String name) {
        this.name = name;
    }

    public void showCardsToPlayer() {
        String cards = "Your cards are ";
        int cardNumber = 1;
        for (String c : whiteCards) {
            cards += cardNumber + ") \u00031,0[" + c + "]\u0003  ";
            cardNumber++;
        }
        ircBot.sendNotice(getName(), cards);
        // ircBot.sendMessage(getName(), cards);
    }

    @Override
    public String toString() {
        return "Player{" + "name=" + getName() + '}';
    }
}
