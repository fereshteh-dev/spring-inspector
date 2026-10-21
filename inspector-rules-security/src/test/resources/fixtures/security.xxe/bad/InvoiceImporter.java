package com.acme.invoices;

import java.io.InputStream;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.stream.XMLInputFactory;
import org.w3c.dom.Document;

class InvoiceImporter {

    Document parse(InputStream xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        return factory.newDocumentBuilder().parse(xml);
    }

    Object stream(InputStream xml) throws Exception {
        return XMLInputFactory.newFactory().createXMLStreamReader(xml);
    }
}
