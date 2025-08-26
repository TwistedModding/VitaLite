package com.tonic.util;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.JarURLConnection;
import java.net.URL;
import java.util.jar.JarFile;

public class RuneliteConfigUtil
{
    public static String fetchUrl()
    {
        String injectedVersion   = getTagValueFromURL("release");
        String injectedFilename  = "injected-client-" + injectedVersion + ".jar";
        return  "https://repo.runelite.net/net/runelite/injected-client/" + injectedVersion + "/" + injectedFilename;
    }
    public static JarFile fetchGamePack() throws Exception
    {
        String injectedUrl = fetchUrl();
        URL jarUrl = new URL("jar:" + injectedUrl + "!/");
        return ((JarURLConnection) jarUrl.openConnection()).getJarFile();
    }

    /**
     * Extracts the value of the specified tag from an XML file at a given URL.
     *
     * @param tagName   The name of the tag to extract.
     * @return The value of the specified tag, or null if not found.
     */
    public static String getTagValueFromURL(String tagName) {
        try {
            // Open a connection to the URL
            URL url = new URL("https://repo.runelite.net/net/runelite/injected-client/maven-metadata.xml");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            // Check if the request was successful
            if (connection.getResponseCode() == 200) {
                try (InputStream inputStream = connection.getInputStream()) {
                    // Parse the XML from the input stream
                    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                    DocumentBuilder builder = factory.newDocumentBuilder();
                    Document document = builder.parse(inputStream);
                    document.getDocumentElement().normalize();

                    // Find the specified tag
                    NodeList nodeList = document.getElementsByTagName(tagName);
                    if (nodeList.getLength() > 0) {
                        return nodeList.item(0).getTextContent();
                    }
                }
            } else {
                System.out.println("Failed to fetch XML. HTTP Response Code: " + connection.getResponseCode());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null; // Return null if the tag is not found or an error occurs
    }
}