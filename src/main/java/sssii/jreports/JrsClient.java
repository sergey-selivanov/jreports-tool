package sssii.jreports;

import java.io.File;
import java.io.IOException;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.StatusType;

import org.glassfish.jersey.client.authentication.HttpAuthenticationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class JrsClient {
    private static Logger log = LoggerFactory.getLogger(JrsClient.class);

    private Client client;
    private String baseUrl;    // http://localhost:8080/jasperserver/rest_v2

    public JrsClient(String baseUrl, String login, String password){
        HttpAuthenticationFeature feature = HttpAuthenticationFeature.basic(login, password);

        client = ClientBuilder.newClient();
        client.register(feature);

        this.baseUrl = baseUrl;
    }

    public boolean deleteResource(String resource){
        WebTarget webTarget = client.target(baseUrl + "/resources" + resource);
        Invocation.Builder invocationBuilder = webTarget.request();
        log.debug("querying " + webTarget.getUri());

        try{
            Response responseJasper = invocationBuilder.delete();

            log.debug("status " + responseJasper.getStatus() + " " + responseJasper.getStatusInfo());
            StatusType st = responseJasper.getStatusInfo();

            if(st.getStatusCode() == Response.Status.NO_CONTENT.getStatusCode()){
                log.info("Successfully deleted: " + resource);
            }
            else if(st.getStatusCode() == Response.Status.NOT_FOUND.getStatusCode()){
                log.error("resource path or ID is not valid: " + resource);
                return false;
            }
            else{
                log.error("unexpected response, status code: " + st.getStatusCode());
                return false;
            }
        }
        catch(Exception ex){
            log.error("Failed to delete " + resource, ex);
            return false;
        }

        return true;
    }

    public boolean importReports(String fileName) throws IOException, InterruptedException{

        //File f = new File("d:/git/nnn-server/jasperReports/jasper-export.zip");
        File f = new File(fileName);
        if(!f.exists()){
            log.error("File not exists: " + f.getAbsolutePath());
            return false;
        }

        log.info("Importing " + f.getAbsolutePath());

        WebTarget webTarget = client.target(baseUrl + "/import")
                .queryParam("includeAccessEvents", "false");

        // http://www.benchresources.net/jersey-2-x-web-service-for-uploadingdownloading-zip-file-java-client/

        Invocation.Builder invocationBuilder = webTarget.request();

        log.debug("querying " + webTarget.getUri());

        //byte[] buf;


//        FileDataBodyPart fdbp = new FileDataBodyPart("import", f);
//        FormDataMultiPart fdmp = new FormDataMultiPart();
//        fdmp.bodyPart(fdbp);


        //Response responseJasper = invocationBuilder.post(Entity.entity(fdmp, MediaType.MULTIPART_FORM_DATA));

        MediaType mt = new MediaType("application", "zip");
        Response responseJasper = invocationBuilder.post(Entity.entity(f, mt));

//        log.debug("media type: " + responseJasper.getMediaType());
//        MultivaluedMap<String, String> headers = responseJasper.getStringHeaders();
//        for(String key: headers.keySet()){
//            log.debug("header " + key + " - " + headers.getFirst(key));
//        }
//
//        log.debug("status " + responseJasper.getStatus() + " " + responseJasper.getStatusInfo());
//        log.debug("received " + responseJasper.getMediaType());

        StatusType st = responseJasper.getStatusInfo();

        if(st.getStatusCode() != Response.Status.OK.getStatusCode()){
            log.error("failed to initiate import");
            return false;
        }



//        InputStream in = responseJasper.readEntity(InputStream.class);
//
//        BufferedReader br = new BufferedReader(new InputStreamReader(in));
//
//        StringBuilder sb = new StringBuilder();
//        String line = br.readLine();
//        while(line != null){
//            sb.append(line);
//            log.debug(line);
//            line = br.readLine();
//        }
//
//        in.close();



        //ExportAnswer ans = ExportAnswer.parse(getResponseText(responseJasper));
        String answerStr = responseJasper.readEntity(String.class);
        ExportAnswer ans = ExportAnswer.parse(answerStr);


        webTarget = client.target(baseUrl + "/import")
                .path(ans.getId())
                .path("state");

        invocationBuilder = webTarget.request();
        log.debug("querying " + webTarget.getUri());

        boolean stop = false;
        while(!stop){
            log.info("Import in progress, waiting for completion...");
            Thread.sleep(1000);
            responseJasper = invocationBuilder.get();

            st = responseJasper.getStatusInfo();
            //String text = getResponseText(responseJasper);
            String text = responseJasper.readEntity(String.class);
            log.debug("status " + responseJasper.getStatus() + " " + responseJasper.getStatusInfo());

            if(st.getStatusCode() == Response.Status.OK.getStatusCode()){
                ans = ExportAnswer.parse(text);
                if(ans.getPhase().equals("finished") && ans.getMessage().equals("Import succeeded.")){
                    log.info("Import successfully completed");
                    stop = true;
                }
            }
            else if(st.getStatusCode() == Response.Status.NOT_FOUND.getStatusCode()){
                // not OK - import failed
                log.warn("Import task not found");
                stop = true;

                log.debug("answer was: " + text);

                return false;
            }
        }

        return true;
    }

//    private String getResponseText(Response resp){
//        try{
//            InputStream in = resp.readEntity(InputStream.class);
//
//            BufferedReader br = new BufferedReader(new InputStreamReader(in));
//
//            StringBuilder sb = new StringBuilder();
//            String line = br.readLine();
//            while(line != null){
//                sb.append(line);
//                //log.debug(line);
//                line = br.readLine();
//            }
//
//            in.close();
//
//            return sb.toString();
//        }
//        catch(IOException ex){
//            log.error("failed to dump", ex);
//        }
//
//        return null;
//    }
}
