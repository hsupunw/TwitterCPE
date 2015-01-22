package org.wso2.uima.collectionProccesingEngine.consumers;

import org.apache.log4j.Logger;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.CASException;
import org.apache.uima.cas.FSIndex;
import org.apache.uima.collection.CasConsumer_ImplBase;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.ResourceProcessException;
import org.wso2.carbon.databridge.agent.thrift.DataPublisher;
import org.wso2.carbon.databridge.agent.thrift.exception.AgentException;
import org.wso2.carbon.databridge.commons.Event;
import org.wso2.carbon.databridge.commons.StreamDefinition;
import org.wso2.carbon.databridge.commons.exception.*;
import org.wso2.uima.collectionProccesingEngine.consumers.util.KeyStoreUtil;
import org.wso2.uima.types.LocationIdentification;
import org.wso2.uima.types.TrafficLevelIdentifier;

import java.net.MalformedURLException;
import java.sql.Timestamp;
import java.util.Date;
import java.util.Iterator;

public class DataBridgeCasConsumer extends CasConsumer_ImplBase{

    private static int twittercounter= 0;

    private static final String STREAM_NAME = "org.wso2.uima.TwitterExtractedFeed";
    private static final String VERSION = "1.0.0";

    private static final String PARAM_SERVER_URL = "serverURL";
    private static final String PARAM_USERNAME = "username";
    private static final String PARAM_PASSWORD = "password";

    private static String streamID = null;
    private static DataPublisher dataPublisher;

    private static Logger logger = Logger.getLogger(DataBridgeCasConsumer.class);

    @Override
    public void processCas(CAS cas) throws ResourceProcessException {

        // run the sample document through the pipeline
        JCas output= null;
        try {
            output = cas.getJCas();
        } catch (CASException e2) {
            // TODO Auto-generated catch block
            e2.printStackTrace();
        }

        String tweetText = "\n"+output.getDocumentText();
        String locationString="";

        FSIndex locationIndex = output.getAnnotationIndex(LocationIdentification.type);
        for (Iterator<LocationIdentification> it = locationIndex.iterator(); it.hasNext();) {
            LocationIdentification annotation = it.next();
            //    System.out.println("AN2...(" + annotation.getBegin() + "," +
            //      annotation.getEnd() + "): " +
            //      annotation.getCoveredText());
            if(!locationString.contains(annotation.getCoveredText()))
                locationString = locationString + annotation.getCoveredText()+" ";
        }



        String trafficLevel = "";
        FSIndex trafficLevelIndex =
                output.getAnnotationIndex(TrafficLevelIdentifier.type);
        for(Iterator<TrafficLevelIdentifier> it = trafficLevelIndex.iterator(); it.hasNext();){
            TrafficLevelIdentifier level = it.next();
            trafficLevel = level.getTrafficLevel();
        }

        //locationString = locationString.substring(0,locationString.length()-1);
        logger.debug("Annotated Location :  " + locationString.trim());
        logger.debug("Annotated Traffic :  " + trafficLevel);


       // locationString = "Colombo";

        //Publish event for a valid stream
        if (streamID != null && !locationString.equals("")) {
         //   System.out.println("Stream ID: " + streamID+"  to be Published");

            try {

                twittercounter++;

             //   String[] locations = locationString.substring(0,locationString.length()-1).split(",");
           //     System.out.println("LOCATIONS DETECTED  : "+locations.length);
             //   for(String location : locations){
                    publishEvents(
                            dataPublisher,
                            streamID,
                            locationString.trim(),
                            trafficLevel,
                            tweetText
                    );
               // }


            } catch (AgentException e1) {
                // TODO Auto-generated catch block
                e1.printStackTrace();
            }

         /*   try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                //ignore
            }
*/

        }

    }


    @Override
    public void initialize() throws ResourceInitializationException {
      // String streamId1 = null;
      //  System.out.println("Starting Statistics Agent");

        KeyStoreUtil.setTrustStoreParams();

       // get the configuration parameters from the descriptor file
       String url = (String)getConfigParameterValue(PARAM_SERVER_URL);
       String username = (String)getConfigParameterValue(PARAM_USERNAME);
       String password = (String)getConfigParameterValue(PARAM_PASSWORD);

        try {

            dataPublisher = new DataPublisher(url,username,password);
            logger.debug("Data Publisher Created");

        } catch (MalformedURLException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        } catch (AgentException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        } catch (AuthenticationException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        } catch (TransportException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }


        try {
            streamID = dataPublisher.findStream(STREAM_NAME, VERSION);
            logger.debug("Stream Definition Already Exists");

        } catch (NoStreamDefinitionExistException | AgentException | StreamDefinitionException e) {
            try {
                 StreamDefinition streamDef = new StreamDefinition(VERSION);
                 streamDef.setNickName("TwitterCEP");
                 streamDef.setDescription("Extracted Data Feed from Tweets");
                 streamDef.addTag("UIMA");
                 streamDef.addTag("CEP");


                 streamID = dataPublisher.defineStream("{" +
                        " 'name':'"+STREAM_NAME+"'," +
                        " 'version':'"+ VERSION +"'," +
                        " 'nickName': 'TwitterCEP'," +
                        " 'description': 'Some Desc'," +
                        " 'tags':['UIMA', 'CEP']," +
                        " 'metaData':[" +
                        "       {'name':'timeStamp','type':'STRING'}" +
                        " ]," +
                        " 'payloadData':[" +
                        "       {'name':'Location','type':'STRING'}," +
                        "       {'name':'TrafficLevel','type':'STRING'},"+
                        "       {'name':'TweetText','type':'STRING'}"+
                        " ]" +
                        "}");

                logger.debug("Stream ID : "+streamID);
                logger.debug("Stream was not found and defined successfully");

            } catch (AgentException | MalformedStreamDefinitionException
                    | StreamDefinitionException
                    | DifferentStreamDefinitionAlreadyDefinedException e1) {
                // TODO Auto-generated catch block
                e1.printStackTrace();
                logger.debug("Stream Definition Failed");
            }


        }

//      /  System.out.println("/////////////////   " + streamId1);
      //  super.initialize();

    }

    private static void publishEvents(DataPublisher dataPublisher, String streamId, String ... payloadArgs)
            throws AgentException {

        Date date= new Date();
        String time=new Timestamp(date.getTime()).toString();
       

        Object[] meta = new Object[]{
                time
        };

        Object[] payload = payloadArgs;

        Object[] correlation = new Object[]{};

        Event statisticsEvent = new Event(streamId, System.currentTimeMillis(),
                meta, correlation, payload);

        dataPublisher.publish(statisticsEvent);
        logger.info("Event Published Via DataBridge Successfully");
    }

}