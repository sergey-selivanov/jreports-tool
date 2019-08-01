package sssii.jreports;

import com.thoughtworks.xstream.XStream;


public class ExportAnswer {

/*
    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
    <state>
        <id>3b32aa60-c780-4a41-b609-02c54f5561bb</id>
        <message>Import in progress.</message>
        <phase>inprogress</phase></state>


<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<state>
<id>1f5471c0-ab67-4e35-8e0a-78af696616eb</id>
<message>Import succeeded.</message>
<phase>finished</phase></state>


 <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
 <errorDescriptor>
 <errorCode>no.such.export.process</errorCode>
 <message>No export task with id 1f5471c0-ab67-4e35-8e0a-78af696616eb.</message>
 <parameters>
 <parameter>1f5471c0-ab67-4e35-8e0a-78af696616eb</parameter></parameters></errorDescriptor>

*/
    public static final String ALIAS = "state";

    private String id = "unknown";
    private String message = "unknown";
    private String phase = "unknown";

    public static ExportAnswer parse(String xml){

        // https://x-stream.github.io/tutorial.html
        XStream xstream = new XStream();
        xstream.ignoreUnknownElements();
        XStream.setupDefaultSecurity(xstream);
        xstream.allowTypes(new Class[] {ExportAnswer.class});
        xstream.alias(ExportAnswer.ALIAS, ExportAnswer.class);
        return (ExportAnswer)xstream.fromXML(xml, new ExportAnswer());
    }

    public ExportAnswer(){

    }


    public String getId() {
        return id;
    }
    public String getMessage() {
        return message;
    }
    public String getPhase() {
        return phase;
    }

}
