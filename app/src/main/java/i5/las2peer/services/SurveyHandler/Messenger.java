package i5.las2peer.services.SurveyHandler;
/**
 * Enum representing different types of messengers.
 */
public enum Messenger {
    SLACK("Slack"),
    ROCKETCHAT("Rocket.Chat"),
    TELEGRAM("Telegram");

    private final String name;

    /**
     * Returns the name of the messenger.
     *
     * @return A string representing the name of the messenger.
     */
    @Override
    public String toString(){
        return this.name;
    }

    /**
     * Constructor for the Messenger enum.
     *
     * @param name The name of the messenger.
     */
    Messenger(String name){
        this.name= name;
    }
}
