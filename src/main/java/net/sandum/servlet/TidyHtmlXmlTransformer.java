package net.sandum.servlet;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import javax.xml.transform.ErrorListener;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.tidy.Configuration;
import org.w3c.tidy.Tidy;

/**
 * @author      osa
 * @since       15-02-2014
 * @version     $Id: TidyHtmlXmlTransformer.java -1 15-02-2014 15:37:26 osa $
 */
public class TidyHtmlXmlTransformer implements SimpleFormatTransformer {

    private final static Logger LOG = LoggerFactory.getLogger(TidyHtmlXmlTransformer.class);

    private final ErrorListener ERROR_LOG = new ErrorListener() {

        public void warning(TransformerException ex) throws TransformerException {
            LOG.warn(null, ex);
        }

        public void error(TransformerException ex) throws TransformerException {
            LOG.error(null, ex);
        }

        public void fatalError(TransformerException ex) throws TransformerException {
            LOG.error(null, ex);
        }
    };

    public TidyHtmlXmlTransformer() {
        TransformerFactory tff = TransformerFactory.newInstance();
        stff = (SAXTransformerFactory) tff;

        tidyProps = new Properties();
        systemIds = new ArrayList<String>();
    }

    public String getSourceMimeType() {
        return "text/html";
    }

    public String getTargetMimeType() {
        return "text/xml";
    }

    public void transform(InputStream is, OutputStream os) throws IOException {
        Tidy jtidy = new Tidy();
        jtidy.setOnlyErrors(true);
        jtidy.getConfiguration().addProps(tidyProps);

        Configuration c = jtidy.getConfiguration();
        c.printConfigOptions(new PrintWriter(System.out), true);

        jtidy.setErrout(new PrintWriter(new Writer() {
            public void write(char[] cbuf, int off, int len) throws IOException {
                // Remove the end of line chars
                while (len > 0 && (cbuf[len - 1] == '\n' || cbuf[len - 1] == '\r'))
                    len--;
                if (len > 0)
                    LOG.debug(String.copyValueOf(cbuf, off, len));
            }

            public void flush() throws IOException {
            }

            public void close() {
            }
        }, true));
        Document doc = jtidy.parseDOM(is, null);

        Source xslInput = new DOMSource(doc);
        Result xslOutput = new StreamResult(os);

        // To force reload stylesheet:
        templates = null;

        try {
            if (templates == null) {
                templates = new LinkedList<Templates>();
                for (String si : systemIds)
                    templates.add(stff.newTemplates(new StreamSource(si)));
                Collections.reverse(templates);
            }
            Transformer transformer = stff.newTransformer();
//                  templates != null ? templates.newTransformer() : stff.newTransformer();
//          transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");

            for (Templates t : templates) {
                TransformerHandler stage = stff.newTransformerHandler(t);
                stage.setResult(xslOutput);
                xslOutput = new SAXResult(stage);
            }

            transformer.setOutputProperty(OutputKeys.METHOD, "xml");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            if (indent != null) {
                transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
                transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            }
            transformer.setErrorListener(ERROR_LOG);

            transformer.transform(xslInput, xslOutput);
        } catch (TransformerException ex) {
            LOG.error("Failed to set up XML serializer", ex);
        }
    }

    private SAXTransformerFactory stff;
    private List<String> systemIds;
    private List<Templates> templates;

    private Integer indent;
    private Properties tidyProps;

    public void init(FormatTransformerConfig cfg) {

        String s = cfg.getInitParameter("indent");
        if (s != null)
            indent = Integer.parseInt(s);

        systemIds = new ArrayList<String>();
        String temps = cfg.getInitParameter("templates");
        if (temps != null) {
            for (String t : temps.split(","))
                try {
                    t = t.trim();
                    URL systemIdUrl = cfg.getResource(t);
                    if (systemIdUrl == null)
                        throw new IllegalArgumentException(temps + ": no such template");
                    addTemplates(systemIdUrl);
                } catch (MalformedURLException ex) {
                    throw new RuntimeException(ex);
                }
        }

        tidyProps.clear();
        for (String propName : cfg.getInitParameterNames())
            if (propName.startsWith("jtidy."))
                setTidyProperty(propName.substring("jtidy.".length()), cfg.getInitParameter(propName));

        tidyProps.list(System.out);
    }

    public void setTidyProperty(String key, String value) {
        tidyProps.setProperty(key, value);
    }

    public void addTemplates(URL stylesheet) {
        systemIds.add(stylesheet.toString());
     // StreamSource ss = new StreamSource(systemId.toString());
     // try {
     //     templates = tff.newTemplates(ss);
     // } catch (TransformerConfigurationException ex) {
     //     throw new RuntimeException(ex);
     // }
    }
}
