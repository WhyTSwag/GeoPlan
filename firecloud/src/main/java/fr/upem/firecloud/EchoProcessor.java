package fr.upem.firecloud;

/**
 * Created by Jeremie on 03/03/2016.
 */
/**
 * Handles an echo request.
 */
public class EchoProcessor implements PayloadProcessor{

    @Override
    public void handleMessage(CcsMessage msg) {
        PseudoDao dao = PseudoDao.getInstance();
        CcsClient client = CcsClient.getInstance();
        String msgId = dao.getUniqueMessageId();
        String jsonRequest =
                CcsClient.createJsonMessage(
                        msg.getFrom(),
                        msgId,
                        msg.getPayload(),
                        null,
                        null, // TTL (null -> default-TTL)
                        false);
        client.send(jsonRequest);
    }

}
