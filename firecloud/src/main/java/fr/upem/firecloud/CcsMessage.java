package fr.upem.firecloud;

/**
 * Created by Jeremie on 03/03/2016.
 */


import java.util.Map;

/**
 * Represents a message for CCS based massaging.
 */
public class CcsMessage {

    /**
     * Recipient-ID.
     */
    private String mFrom;
    /**
     * Sender app's package.
     */
    private String mCategory;
    /**
     * Unique id for this message.
     */
    private String mMessageId;
    /**
     * Payload data. A String in Json format.
     */
    private Map<String, String> mPayload;

    public CcsMessage(String from, String category, String messageId, Map<String, String> payload) {
        mFrom = from;
        mCategory = category;
        mMessageId = messageId;
        mPayload = payload;
    }

    public String getFrom() {
        return mFrom;
    }

    public String getCategory() {
        return mCategory;
    }

    public String getMessageId() {
        return mMessageId;
    }

    public Map<String, String> getPayload() {
        return mPayload;
    }
}
