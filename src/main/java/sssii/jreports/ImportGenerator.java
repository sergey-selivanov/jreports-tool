package sssii.jreports;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.CDATASection;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class ImportGenerator {

    private static Logger log = LoggerFactory.getLogger(ImportGenerator.class);

    private DocumentBuilder docBuilder;
    private TransformerFactory transformerFactory;
    private XPath xpath;
    private String timestamp;
    private String datasourceUri;
    private Document dataSource;

    private ArrayList<Pattern> excludeFilenameRegexp = new ArrayList<>();

    public ImportGenerator() throws ToolException
    {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            //dbf.setNamespaceAware(true);

            docBuilder = dbf.newDocumentBuilder();

            transformerFactory = TransformerFactory.newInstance();
            transformerFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);

            xpath = XPathFactory.newInstance().newXPath();

            // <creationDate>2016-10-13T13:53:07.000Z</creationDate>
//            Calendar cal = Calendar.getInstance();
//            cal.setTimeZone(TimeZone.getTimeZone("UTC"));
//            Date now = new Date();
            //ZonedDateTime zdt = new
            Instant now = Instant.now();

            //SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
            //timestamp = sdf.format(now);
            timestamp = now.toString();

            //sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            //timestamp2 = ins.toString();
        } catch (ParserConfigurationException | TransformerConfigurationException e) {
            throw new ToolException("Failed to instantiate ImportGenerator", e);
        }
    }

    public void process(String[] sourceDirs, String targetDir, String targetZip,
            String resource,
            String dsName, String dsDriver, String dsUrl, String dsLogin, String dsPassword,
            String excludesFile
            ) throws ToolException
    {
        try {
            // Some sanity checks

            for(String srcD: sourceDirs) {
                File srcDir = new File(srcD);
                if (!srcDir.isDirectory()) {
                    throw new ToolException("File " + srcDir.getAbsolutePath() + " does not exist or is not a directory");
                }
            }

            boolean isDeleteTargetDir = false;
            if(targetDir == null){
                // use temp dir and delete after processing
                Path tempdir = Files.createTempDirectory("jreports-tool");
                //File f = new File(tempdir.toAbsolutePath().toString());
                //f.deleteOnExit();    // dir is not deleted if not empty
                targetDir = tempdir.toAbsolutePath().toString();
                log.debug("Temp directory: " + targetDir);
                isDeleteTargetDir = true;
            }
            else{
                // specific dir, will not be deleted
                File f = new File(targetDir);
                log.info("Output directory: " + f.getAbsolutePath());
                if (!f.exists()) {
                    log.debug("Directory " + f.getAbsolutePath() + " does not exist, will be created");
                } else {
                    if (f.isDirectory()) {
                        String[] list = f.list();
                        if (list.length > 0) {
                            throw new ToolException("Directory " + f.getAbsolutePath() + " is not empty"); // TODO test
                        }
                    } else {
                        throw new ToolException("File " + f.getAbsolutePath() + " exists and is not a directory");
                    }
                }
            }

            if(targetZip != null){
                File f = new File(targetZip);
                if (f.exists()) {
                    throw new ToolException("File " + f.getAbsolutePath() + " already exists");
                }
            }

            // Prepare filename filter
            if (excludesFile != null) {
                prepareExcludes(excludesFile);
            }

            datasourceUri = resource + "/" + dsName;

            // prepare datasource

            dataSource = docBuilder.parse(ImportGenerator.class.getResourceAsStream("/templates/dataSource.xml"));
            Node n = (Node) xpath.evaluate("/jdbcDataSource/name", dataSource.getDocumentElement(), XPathConstants.NODE);
            n.setTextContent(dsName);
            n = (Node) xpath.evaluate("/jdbcDataSource/label", dataSource.getDocumentElement(), XPathConstants.NODE);
            n.setTextContent(dsName);
            n = (Node) xpath.evaluate("/jdbcDataSource/creationDate", dataSource.getDocumentElement(), XPathConstants.NODE);
            n.setTextContent(timestamp);
            n = (Node) xpath.evaluate("/jdbcDataSource/updateDate", dataSource.getDocumentElement(), XPathConstants.NODE);
            n.setTextContent(timestamp);
            n = (Node) xpath.evaluate("/jdbcDataSource/driver", dataSource.getDocumentElement(), XPathConstants.NODE);
            n.setTextContent(dsDriver);
            n = (Node) xpath.evaluate("/jdbcDataSource/connectionUrl", dataSource.getDocumentElement(), XPathConstants.NODE);
            n.setTextContent(dsUrl);
            n = (Node) xpath.evaluate("/jdbcDataSource/connectionUser", dataSource.getDocumentElement(), XPathConstants.NODE);
            n.setTextContent(dsLogin);
            n = (Node) xpath.evaluate("/jdbcDataSource/connectionPassword", dataSource.getDocumentElement(), XPathConstants.NODE);
            n.setTextContent(dsPassword);

            // Looks good so far, process files

            // Target dir: root + resources subdir
            File f = new File(targetDir + File.separator + "resources" + resource);
            for(String srcD: sourceDirs) {
                File srcDir = new File(srcD);
                processSourceDirectory(srcDir, f, resource, 0);
            }

            // TODO: remove insertionAnchor element from all .folder.xml files here. It seems jasperserver accepts and ignores this element but anyway.

            // write index
            Document index = docBuilder.parse(ImportGenerator.class.getResourceAsStream("/templates/index.xml"));
            n = (Node)xpath.evaluate("/export/module/folder", index.getDocumentElement(), XPathConstants.NODE);
            n.setTextContent(resource);
            DOMSource domsrc = new DOMSource(index);
            FileOutputStream fos = new FileOutputStream(new File(targetDir + File.separator + "index.xml"));
            StreamResult streamResult = new StreamResult(fos);
            transformerFactory.newTransformer().transform(domsrc, streamResult);
            fos.close();

            // zip to file
            if(targetZip != null){
                File zipFile = new File(targetZip);
                log.info("Writing zip file " + zipFile.getAbsolutePath());
                ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile));
                zipDir(zos, new File(targetDir), 0, null);
                zos.close();
            }

            if(isDeleteTargetDir){
                deleteDir(new File(targetDir));
            }

        } catch (SAXException | IOException | XPathExpressionException | TransformerException e) {
            throw new ToolException("Failed", e);
        }
    }

    private void processSourceDirectory(File srcDir, File targetDir, String resourceDir, int level) throws ToolException
    {
        log.info("Processing source directory " + srcDir.getAbsolutePath());

        try{
            // prepare .folder
            //Document dotfolder = docBuilder.parse(ImportGenerator.class.getResourceAsStream("/templates/dot-folder.xml"));
            //Node anchor = (Node)xpath.evaluate("/folder/insertionAnchor", dotfolder.getDocumentElement(), XPathConstants.NODE);

            targetDir.mkdirs();

            File dotFolderFile = new File(targetDir.getAbsolutePath() + File.separator + ".folder.xml");
            if(!dotFolderFile.exists()) {
                Files.copy(ImportGenerator.class.getResourceAsStream("/templates/dot-folder.xml"), dotFolderFile.toPath());
            }

            Document dotfolder = docBuilder.parse(dotFolderFile);
            Node anchor = (Node)xpath.evaluate("/folder/insertionAnchor", dotfolder.getDocumentElement(), XPathConstants.NODE);

            // recursively process subdirs
            File[] files = srcDir.listFiles(new FileFilter() {

                @Override
                public boolean accept(File file) {

                    if(file.isDirectory()){
                        for(Pattern exclude: excludeFilenameRegexp){
                            if(exclude.matcher(file.getName()).matches()){
                                log.debug("Excluded: " + file.getName());
                                return false;
                            }
                        }
                        return true;
                    }
                    return false;
                }});

            for(File subdir: files){
                File f = new File(targetDir.getAbsolutePath() + File.separator + subdir.getName());
                processSourceDirectory(subdir, f, resourceDir + "/" + subdir.getName(), level + 1);

                Element elFolder = dotfolder.createElement("folder");
                elFolder.setTextContent(subdir.getName());
                dotfolder.getDocumentElement().insertBefore(elFolder, anchor);
            }

            // find regular files in this dir
            files = srcDir.listFiles(new FileFilter() {
                @Override
                public boolean accept(File file) {

                    if(file.isDirectory()){ return false; }
                    for(Pattern exclude: excludeFilenameRegexp){
                        if(exclude.matcher(file.getName()).matches()){
                            log.debug("Excluded: " + file.getName());
                            return false;
                        }
                    }

//                    if(file.getName().toLowerCase().endsWith(".jrxml")){ return true; }
                    //log.debug("skip " + file.getName());
//                    return false;
                    return true;
                }
            });

            if(files.length == 0){
                log.info("No suitable files found in " + srcDir.getAbsolutePath());
                //return;    // allow to write .folder below
            }

            Hashtable<String, File> reports = new Hashtable<>();
            Hashtable<String, File> subreports = new Hashtable<>();
            Hashtable<String, File> others = new Hashtable<>();
            Hashtable<String, File> othersUsed = new Hashtable<>();    // e.g images in reports

            // detect subreports
            for(File file: files){

                if(!file.getName().toLowerCase().endsWith(".jrxml")){
                    // other content file
                    String fname = file.getName();
                    log.debug("non-report file: " + fname);
                    others.put(fname, file);
                }
                else{
                    // jrxml report
                    Document doc = docBuilder.parse(new FileInputStream(file));

                    // XML is OK
                    String fname = file.getName().substring(0, file.getName().indexOf("."));
                    log.debug("report: " + fname);
                    reports.put(fname, file);

                    // find subreports
                    NodeList nl = doc.getElementsByTagName("subreportExpression");
                    for(int i = 0; i < nl.getLength(); i++){
                        Node n = nl.item(i);
                        String subrep = n.getTextContent();

                        subrep = subrep.substring(1, subrep.indexOf("."));    // strips starting " and rest from dot
                        log.debug("subreport: " + subrep);

                        File subrepFile = new File(srcDir.getAbsolutePath() + File.separator + subrep + ".jrxml");
                        if(!subrepFile.exists()){
                            throw new ToolException("Expected subreport file " + subrepFile.getAbsolutePath() + " not found");
                        }

                        subreports.put(subrep, subrepFile);
                    }

                    // find images
                    nl = doc.getElementsByTagName("imageExpression");
                    for(int i = 0; i < nl.getLength(); i++){
                        Node n = nl.item(i);
                        String imgName = n.getTextContent();
                        imgName = imgName.substring(1, imgName.length() - 1); // strip " "
                        File imgFile = new File(srcDir.getAbsolutePath() + File.separator + imgName);
                        if(!imgFile.exists()){
                            //throw new ToolException("Expected image file " + imgFile.getAbsolutePath() + " not found");
                            log.info("Image expression '" + imgName + "' is not an existing file, assume expression");
                        }
                        else {
                            othersUsed.put(imgName, imgFile);
                        }
                    }
                }
            }

            // remove explicitly used subreports from reports list
            for(String key: subreports.keySet()){
                reports.remove(key);
            }

            for(String key: othersUsed.keySet()){
                others.remove(key);
            }

            // process found reports
            //targetDir.mkdirs();
            for(String key: reports.keySet()){
                processReportFile(reports.get(key), resourceDir, key, targetDir);

                Element elResource = dotfolder.createElement("resource");
                //elResource.setTextContent(key);
                String targetName = key.replace("-", "_");
                elResource.setTextContent(targetName);
                dotfolder.getDocumentElement().insertBefore(elResource, anchor);
            }

            // process other content
            for(String key: others.keySet()){
                processResourceFile(others.get(key), resourceDir, targetDir);

                // filenames with '-' in the name are not imported, IDs should use underscore.
                // TODO: is this also true for reports, looks like yes?
                String targetName = key.replace("-", "_");
                Element elResource = dotfolder.createElement("resource");
                elResource.setTextContent(targetName);
                dotfolder.getDocumentElement().insertBefore(elResource, anchor);
            }

            // create datasource
            if(level == 0){
                // write datasource here
                Node n = (Node)xpath.evaluate("/jdbcDataSource/folder", dataSource.getDocumentElement(), XPathConstants.NODE);
                n.setTextContent(resourceDir);
                n = (Node)xpath.evaluate("/jdbcDataSource/name", dataSource.getDocumentElement(), XPathConstants.NODE);
                String dsName = n.getTextContent();
                DOMSource domsrc = new DOMSource(dataSource);
                FileOutputStream fos = new FileOutputStream(targetDir.getAbsolutePath() + File.separator + dsName + ".xml");
                StreamResult streamResult = new StreamResult(fos);
                transformerFactory.newTransformer().transform(domsrc, streamResult);
                fos.close();

                Element elResource = dotfolder.createElement("resource");
                elResource.setTextContent(dsName);
                dotfolder.getDocumentElement().insertBefore(elResource, anchor);
            }

            // remove anchor element
            //dotfolder.getDocumentElement().removeChild(anchor);

            Node n = (Node)xpath.evaluate("/folder/parent", dotfolder.getDocumentElement(), XPathConstants.NODE);
            String parent = resourceDir.substring(0, resourceDir.lastIndexOf("/"));
            n.setTextContent(parent);
            n = (Node)xpath.evaluate("/folder/name", dotfolder.getDocumentElement(), XPathConstants.NODE);
            n.setTextContent(targetDir.getName());
            n = (Node)xpath.evaluate("/folder/label", dotfolder.getDocumentElement(), XPathConstants.NODE);
            n.setTextContent(targetDir.getName());
            n = (Node)xpath.evaluate("/folder/creationDate", dotfolder.getDocumentElement(), XPathConstants.NODE);
            n.setTextContent(timestamp);
            n = (Node)xpath.evaluate("/folder/updateDate", dotfolder.getDocumentElement(), XPathConstants.NODE);
            n.setTextContent(timestamp);

            // save .folder
            DOMSource domsrc = new DOMSource(dotfolder);
            //FileOutputStream fos = new FileOutputStream(targetDir.getAbsolutePath() + File.separator + ".folder.xml");
            FileOutputStream fos = new FileOutputStream(dotFolderFile);
            StreamResult streamResult = new StreamResult(fos);
            transformerFactory.newTransformer().transform(domsrc, streamResult);
            fos.close();
        }
        catch(SAXException | IOException | XPathExpressionException | TransformerException ex){
            throw new ToolException("Failed to process " + srcDir.getName(), ex);
        }
    }

    private void processReportFile(File reportFile, String resourceDir, String reportUnitName, File ruTargetDir) throws ToolException{
        //log.info("Report: " + reportFile.getAbsolutePath());
        log.info("Report: " + reportFile.getName());

        try {

            String fixedReportUnitName = reportUnitName.replace("-", "_");

            // directory for subreports and images
            String filesSubdirName = fixedReportUnitName + "_files";
            File filesSubdir = new File(ruTargetDir.getAbsolutePath() + File.separator + filesSubdirName);
            filesSubdir.mkdirs();

            Document mainReport = docBuilder.parse(new FileInputStream(reportFile));

            // prepare report unit
            Document ruTemplate = docBuilder.parse(ImportGenerator.class.getResourceAsStream("/templates/reportUnit.xml"));

            Node anchor = (Node)xpath.evaluate("/reportUnit/insertionAnchor", ruTemplate.getDocumentElement(), XPathConstants.NODE);

            // get parameters
            //NodeList nl = mainReport.getElementsByTagName("parameter");
            NodeList nl = (NodeList)xpath.evaluate("/jasperReport/parameter", mainReport.getDocumentElement(), XPathConstants.NODESET);
            for(int i = 0; i < nl.getLength(); i++){
                Node n = nl.item(i);
                NamedNodeMap attrs = n.getAttributes();
                String paramName = attrs.getNamedItem("name").getNodeValue();
                String paramClass = attrs.getNamedItem("class").getNodeValue();
                log.trace(attrs.getNamedItem("name").getNodeValue() + " - " + attrs.getNamedItem("class").getNodeValue());

                // fill inputControl
                Document inputControlTemplate = docBuilder.parse(ImportGenerator.class.getResourceAsStream("/templates/inputControlElement.xml"));
                Element el = inputControlTemplate.getDocumentElement();

                Node n2 = (Node)xpath.evaluate("/inputControl/localResource/folder", el, XPathConstants.NODE);
                String val = resourceDir + "/" + filesSubdirName;
                n2.setTextContent(val);

                n2 = (Node)xpath.evaluate("/inputControl/localResource/name", el, XPathConstants.NODE);
                n2.setTextContent(paramName);
                n2 = (Node)xpath.evaluate("/inputControl/localResource/label", el, XPathConstants.NODE);
                n2.setTextContent(paramName);
                n2 = (Node)xpath.evaluate("/inputControl/localResource/creationDate", el, XPathConstants.NODE);
                n2.setTextContent(timestamp);
                n2 = (Node)xpath.evaluate("/inputControl/localResource/updateDate", el, XPathConstants.NODE);
                n2.setTextContent(timestamp);
                // type = 2: single value
                n2 = (Node)xpath.evaluate("/inputControl/localResource/dataType/localResource/folder", el, XPathConstants.NODE);
                val = resourceDir + "/" + filesSubdirName + "/" + paramName + "_files";
                n2.setTextContent(val);
                n2 = (Node)xpath.evaluate("/inputControl/localResource/dataType/localResource/creationDate", el, XPathConstants.NODE);
                n2.setTextContent(timestamp);
                n2 = (Node)xpath.evaluate("/inputControl/localResource/dataType/localResource/updateDate", el, XPathConstants.NODE);
                n2.setTextContent(timestamp);
                n2 = (Node)xpath.evaluate("/inputControl/localResource/dataType/localResource/type", el, XPathConstants.NODE);
                switch(paramClass){
                    case "java.lang.String":
                        n2.setTextContent("1");
                        break;
                    case "java.lang.Integer":
                        n2.setTextContent("2");
                        break;
                    case "java.sql.Date":
                        n2.setTextContent("3");
                        break;
                    default:
                        throw new ToolException("Unexpected report parameter type: " + paramName + " - " + paramClass);
                }

                //ruTemplate.adoptNode(el); // xsi:type and exportedWithPermissions attribute values lost http://stackoverflow.com/questions/20402193/why-node-created-by-document-adoptnodenode-does-not-contain-values-for-attribu
                Node newNode = ruTemplate.importNode(el, true);

                ruTemplate.getDocumentElement().insertBefore(newNode, anchor);
            }

            HashSet<String> usedImages = new HashSet<>();

            // copy image files
            //nl = mainReport.getElementsByTagName("imageExpression");
            nl = (NodeList)xpath.evaluate("//imageExpression", mainReport.getDocumentElement(), XPathConstants.NODESET);
            for(int i = 0; i < nl.getLength(); i++){
                Node n = nl.item(i);
                String imgFileName = n.getTextContent();
                imgFileName = imgFileName.substring(1, imgFileName.length() - 1); // strip " "
                log.debug("image: " + imgFileName);

                if(usedImages.contains(imgFileName)){
                    log.debug("Already used in this report: " + imgFileName);
                    continue;    // TODO: node left not updated? should be updated
                }

                // TODO any files with "-" give import failure, need to replace to underscore everywhere
                String fixedImgFileName = imgFileName.replace("-", "_");

                //String dataFileName = imgFileName + ".data";
                String dataFileName = fixedImgFileName + ".data";
                File src = new File(reportFile.getParentFile().getAbsolutePath() + File.separator + imgFileName);

                if(!src.exists()) {
                    log.info("Image expression '" + imgFileName + "' is not an existing file, assume expression, leave unchanged");
                    continue;
                }

                File dst = new File(filesSubdir.getAbsolutePath() + File.separator + dataFileName);
                Files.copy(src.toPath(), dst.toPath());    // may have several references but not instances or descriptors of the same image

                usedImages.add(imgFileName);

                // modify reference in main report file
                //n.setTextContent("repo:" + imgFileName);    // TODO should this be written as CDATA?
                //n.setTextContent(imgFileName);
                //n.setTextContent("<![CDATA[repo:" + imgFileName + "]]>");
                //CDATASection cdata = mainReport.createCDATASection("\"repo:" + imgFileName + "\"");
                CDATASection cdata = mainReport.createCDATASection("\"repo:" + fixedImgFileName + "\"");
                n.setTextContent("");
                n.appendChild(cdata);

                // add to report unit
                Document resourceTemplate = docBuilder.parse(ImportGenerator.class.getResourceAsStream("/templates/resourceElement.xml"));
                Element el = resourceTemplate.getDocumentElement();

                Node n2 = (Node)xpath.evaluate("/resource/localResource/@dataFile", el, XPathConstants.NODE);
                n2.setNodeValue(dataFileName);

                n2 = (Node)xpath.evaluate("/resource/localResource/folder", el, XPathConstants.NODE);
                String val = resourceDir + "/" + filesSubdirName;
                n2.setTextContent(val);
                n2 = (Node)xpath.evaluate("/resource/localResource/name", el, XPathConstants.NODE);
                //n2.setTextContent(imgFileName);
                n2.setTextContent(fixedImgFileName);
                n2 = (Node)xpath.evaluate("/resource/localResource/label", el, XPathConstants.NODE);
                //n2.setTextContent(imgFileName);
                n2.setTextContent(fixedImgFileName);
                n2 = (Node)xpath.evaluate("/resource/localResource/creationDate", el, XPathConstants.NODE);
                n2.setTextContent(timestamp);
                n2 = (Node)xpath.evaluate("/resource/localResource/updateDate", el, XPathConstants.NODE);
                n2.setTextContent(timestamp);
                n2 = (Node)xpath.evaluate("/resource/localResource/fileType", el, XPathConstants.NODE);
                n2.setTextContent("img");

                Node newNode = ruTemplate.importNode(el, true);
                ruTemplate.getDocumentElement().insertBefore(newNode, anchor);
            }

            // copy subreport files
            //nl = mainReport.getElementsByTagName("subreportExpression");
            nl = (NodeList)xpath.evaluate("//subreportExpression", mainReport.getDocumentElement(), XPathConstants.NODESET);
            for(int i = 0; i < nl.getLength(); i++){
                Node n = nl.item(i);
                String subrFileName = n.getTextContent();
                subrFileName = subrFileName.substring(1, subrFileName.length() - 1); // strip " "
                subrFileName = subrFileName.replace(".jasper", ".jrxml");    // we need source, not compiled
                log.debug("subreport: " + subrFileName);

                String dataFileName = subrFileName + ".data";
                File src = new File(reportFile.getParentFile().getAbsolutePath() + File.separator + subrFileName);
                File dst = new File(filesSubdir.getAbsolutePath() + File.separator + dataFileName);
                //Files.copy(src.toPath(), dst.toPath());    // should not have many instances of same subreport
                Files.copy(src.toPath(), dst.toPath(), StandardCopyOption.REPLACE_EXISTING);    // TODO test

                // modify reference in main report file
                //n.setTextContent("repo:" + subrFileName);
                //n.setTextContent(subrFileName);
                //n.setTextContent("<![CDATA[repo:" + subrFileName + "]]>");
                CDATASection cdata = mainReport.createCDATASection("\"repo:" + subrFileName + "\"");    // TODO already added to the current node??
                n.setTextContent("");
                n.appendChild(cdata);

                // add to report unit
                Document resourceTemplate = docBuilder.parse(ImportGenerator.class.getResourceAsStream("/templates/resourceElement.xml"));
                Element el = resourceTemplate.getDocumentElement();

                Node n2 = (Node)xpath.evaluate("/resource/localResource/@dataFile", el, XPathConstants.NODE);
                n2.setNodeValue(dataFileName);
                n2 = (Node)xpath.evaluate("/resource/localResource/folder", el, XPathConstants.NODE);
                String val = resourceDir + "/" + filesSubdirName;
                n2.setTextContent(val);
                n2 = (Node)xpath.evaluate("/resource/localResource/name", el, XPathConstants.NODE);
                n2.setTextContent(subrFileName);
                n2 = (Node)xpath.evaluate("/resource/localResource/label", el, XPathConstants.NODE);
                n2.setTextContent(subrFileName);
                n2 = (Node)xpath.evaluate("/resource/localResource/creationDate", el, XPathConstants.NODE);
                n2.setTextContent(timestamp);
                n2 = (Node)xpath.evaluate("/resource/localResource/updateDate", el, XPathConstants.NODE);
                n2.setTextContent(timestamp);
                n2 = (Node)xpath.evaluate("/resource/localResource/fileType", el, XPathConstants.NODE);
                n2.setTextContent("jrxml");

                Node newNode = ruTemplate.importNode(el, true);
                ruTemplate.getDocumentElement().insertBefore(newNode, anchor);
            }

            // write main report file
            DOMSource domsrc = new DOMSource(mainReport);
            FileOutputStream fos = new FileOutputStream(filesSubdir.getAbsolutePath() + File.separator + "main_jrxml.data");
            StreamResult streamResult = new StreamResult(fos);
            transformerFactory.newTransformer().transform(domsrc, streamResult);
            fos.close();

            // finalize and write report unit file
            Node n = (Node)xpath.evaluate("/reportUnit/folder", ruTemplate.getDocumentElement(), XPathConstants.NODE);
            n.setTextContent(resourceDir);
            n = (Node)xpath.evaluate("/reportUnit/name", ruTemplate.getDocumentElement(), XPathConstants.NODE);
            n.setTextContent(fixedReportUnitName);
            n = (Node)xpath.evaluate("/reportUnit/label", ruTemplate.getDocumentElement(), XPathConstants.NODE);
            n.setTextContent(reportUnitName);
            n = (Node)xpath.evaluate("/reportUnit/creationDate", ruTemplate.getDocumentElement(), XPathConstants.NODE);
            n.setTextContent(timestamp);
            n = (Node)xpath.evaluate("/reportUnit/updateDate", ruTemplate.getDocumentElement(), XPathConstants.NODE);
            n.setTextContent(timestamp);
            n = (Node)xpath.evaluate("/reportUnit/mainReport/localResource/folder", ruTemplate.getDocumentElement(), XPathConstants.NODE);
            n.setTextContent(resourceDir + "/" + fixedReportUnitName + "_files");
            n = (Node)xpath.evaluate("/reportUnit/mainReport/localResource/creationDate", ruTemplate.getDocumentElement(), XPathConstants.NODE);
            n.setTextContent(timestamp);
            n = (Node)xpath.evaluate("/reportUnit/mainReport/localResource/updateDate", ruTemplate.getDocumentElement(), XPathConstants.NODE);
            n.setTextContent(timestamp);

            n = (Node)xpath.evaluate("/reportUnit/dataSource/uri", ruTemplate.getDocumentElement(), XPathConstants.NODE);
            n.setTextContent(datasourceUri);

            // remove anchor element
            ruTemplate.getDocumentElement().removeChild(anchor);

            domsrc = new DOMSource(ruTemplate);
            fos = new FileOutputStream(ruTargetDir.getAbsolutePath() + File.separator + fixedReportUnitName + ".xml");
            streamResult = new StreamResult(fos);
            transformerFactory.newTransformer().transform(domsrc, streamResult);
            fos.close();
        } catch (IOException | SAXException | TransformerException | XPathExpressionException | DOMException e) {
            throw new ToolException("Failed to process report " + reportUnitName, e);
        }

    }

    private void processResourceFile(File contentFile, String resourceDir, File targetDir) throws ToolException{
        try {
            String targetName = contentFile.getName();

            // for some reason, filenames with '-' in the name are not imported. Is this true for reports?
            // TODO: CDATA?
            targetName = targetName.replace("-", "_");

            File targetFile = new File(targetDir.getAbsolutePath() + File.separator + targetName);
            Files.copy(contentFile.toPath(), targetFile.toPath());

            Document descriptor = docBuilder.parse(ImportGenerator.class.getResourceAsStream("/templates/contentResource.xml"));
            Element el = descriptor.getDocumentElement();
            Node n = (Node)xpath.evaluate("/contentResource/@dataFile", el, XPathConstants.NODE);
            n.setNodeValue(targetName);
            n = (Node)xpath.evaluate("/contentResource/folder", el, XPathConstants.NODE);
            n.setTextContent(resourceDir);
            n = (Node)xpath.evaluate("/contentResource/name", el, XPathConstants.NODE);
            n.setTextContent(targetName);
            n = (Node)xpath.evaluate("/contentResource/label", el, XPathConstants.NODE);
            n.setTextContent(targetName);
            n = (Node)xpath.evaluate("/contentResource/creationDate", el, XPathConstants.NODE);
            n.setTextContent(timestamp);
            n = (Node)xpath.evaluate("/contentResource/updateDate", el, XPathConstants.NODE);
            n.setTextContent(timestamp);

            DOMSource domsrc = new DOMSource(descriptor);
            FileOutputStream fos = new FileOutputStream(targetDir.getAbsolutePath() + File.separator + targetName + ".xml");
            StreamResult streamResult = new StreamResult(fos);
            transformerFactory.newTransformer().transform(domsrc, streamResult);
            fos.close();

        } catch (IOException | SAXException | XPathExpressionException | TransformerException e) {
            throw new ToolException("Failed to process content file", e);
        }
    }

    private void prepareExcludes(String excludesFilename) throws ToolException
    {
        File excludesFile = new File(excludesFilename);
        if(!excludesFile.isFile()){
            throw new ToolException("File " + excludesFile.getAbsolutePath() + " not exists or is not a file");
        }

        excludeFilenameRegexp.clear();

        BufferedReader br;
        try {
            br = new BufferedReader(new FileReader(excludesFile));
            String line;
            while((line = br.readLine()) != null){
                line = line.trim();
                if(!(line.startsWith("#") || line.isEmpty())){
                    excludeFilenameRegexp.add(Pattern.compile(line));
                }
            }
            br.close();
        } catch (IOException ex) {
            throw new ToolException("Bad file: " + excludesFile.getAbsolutePath(), ex);
        }
    }

    private void zipDir(ZipOutputStream zos, File dir, int level, String prefixPath) throws ToolException
    {
        try{
            if(level == 0){
                prefixPath = dir.getAbsolutePath();
            }

            File[] files = dir.listFiles();
            byte[] buffer = new byte[10240];
            for(File f: files){
                if(f.isDirectory()){

                    // it seems jasper server only imports with /
                    String entryName = f.getAbsolutePath().replace(prefixPath, "").substring(1).replace("\\", "/") + "/";
                    log.trace("zip dir entry: " + entryName);
                    ZipEntry entry = new ZipEntry(entryName);
                    zos.putNextEntry(entry);
                    zos.closeEntry();

                    zipDir(zos, f, level + 1, prefixPath);
                }
                else{
                    FileInputStream fis = new FileInputStream(f);
                    String entryName = f.getAbsolutePath().replace(prefixPath, "").substring(1).replace("\\", "/");
                    log.trace("zip entry: " + entryName);
                    ZipEntry entry = new ZipEntry(entryName);
                    zos.putNextEntry(entry);
                    int count = 0;
                    while((count = fis.read(buffer)) != -1){
                        zos.write(buffer, 0, count);
                    }
                    fis.close();
                    zos.closeEntry();
                }
            }
        }
        catch(SecurityException | IOException ex){
            throw new ToolException("Failed to zip", ex);
        }
    }

    private void deleteDir(File dir) throws ToolException{
        log.trace("deleting " + dir.getAbsolutePath());
        File[] list = dir.listFiles();
        for(File f: list){
            if(f.isDirectory()){
                deleteDir(f);
            }
            else{
                if(!f.delete()){
                    throw new ToolException("Failed to delete " + f);
                }
            }
        }

        if(!dir.delete()){
            throw new ToolException("Failed to delete " + dir);
        }
    }

    public String nodeToString(Node node) throws ToolException {
        StringWriter sw = new StringWriter();
        try {
            Transformer t = TransformerFactory.newInstance().newTransformer();
            t.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            t.setOutputProperty(OutputKeys.INDENT, "yes");
            t.transform(new DOMSource(node), new StreamResult(sw));
        } catch (TransformerException te) {
            throw new ToolException("Transformer Exception", te);
        }
        return sw.toString();
    }
}
