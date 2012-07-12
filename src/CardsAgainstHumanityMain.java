
public class CardsAgainstHumanityMain {
	public static void main(String[] args) throws Exception {
		CardsAgainstHumanity bot = new CardsAgainstHumanity();
		bot.setVerbose(true);
		bot.connect("irc.gamesurge.net");
		bot.joinChannel("#joe.to");
		bot.setMessageDelay(2300);
	}

}
