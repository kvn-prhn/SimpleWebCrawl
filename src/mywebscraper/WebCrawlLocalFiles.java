
package mywebscraper;

import java.io.BufferedReader; 
import java.io.File;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This is a web page crawler which will cache pages based on the URL used 
 * to access the page. 
 * @author Kevin
 */
public class WebCrawlLocalFiles {

    private final String[] excluded_suffixes = {
        ".jpg", ".png", ".gif", ".pdf", 
        ".css", ".js", ".ico"
    };
    private List<URL> to_explore;  // going to treat it like a queue.
    private Map<String, File> loaded_pages;
    private Path page_save_location;
    
    public WebCrawlLocalFiles(Path page_save_location, URL startingPoint) {
        this.page_save_location = page_save_location.toAbsolutePath();
        to_explore = Collections.synchronizedList(new ArrayList<URL>());
        to_explore.add(startingPoint);
        loaded_pages = Collections.synchronizedMap(new HashMap<String, File>());
        // each string is a file name referring to the file.
        // the string is a hash of the URL, and the file
        // contains the contents from that URL. 
        
        System.out.println("Reading existing pages in  " + this.page_save_location.toString());
        // read and get all of the initial files to refer to for the 
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(this.page_save_location)) {
            for (Path file: stream) {
                String fileName = file.getFileName().toString().substring(0, file.getFileName().toString().indexOf('.'));
                loaded_pages.put(fileName, file.toFile()); 
            }
        } catch (Exception e) { 
            System.err.println(e);
        }
        System.out.println("number of pages found: " + loaded_pages.size());
        System.out.println("to_explore size: " + to_explore.size());
    }
    
    public String urlToStringHash(URL url) {
        return "page" + url.toString().hashCode();
    }
    
    // this should only be an internal call later
    protected ArrayList<URL> getUrlsInPage(URL page) {
        String page_contents = "";
        ArrayList<URL> page_links = new ArrayList<>();
        String baseUrlStr = page.getProtocol() + "://" + page.getHost();
        String pageUrlHash = urlToStringHash(page);
        
        // check if page was already loaded.
        if (loaded_pages.containsKey(pageUrlHash)) { 
            System.out.println("! local file read");
            File file_to_read = loaded_pages.get(pageUrlHash);
            try {
                BufferedReader reader = new BufferedReader(new FileReader(file_to_read));
                String line; 
                boolean passedFirstSection = false;
                while ((line = reader.readLine()) != null) { 
                    if (passedFirstSection) {
                        page_contents += line;
                    } else {
                        if (line.startsWith("%%")) {
                            passedFirstSection = true;
                        }
                    }
                } 
                reader.close();
            } catch(Exception e) {
                System.err.println("Failed reading from a local page file: " + e);
            }
        } else { 
            // if not, then load the contents and then save it to a file. 
            HttpURLConnection conn = null;
            boolean isHtmlPage = false;
            try { 
                conn = (HttpURLConnection)page.openConnection();
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String line; 
                while ((line = reader.readLine()) != null) { 
                    page_contents += line;
                } 
                // make sure the first line has the <!DOCTYPE html> thing
                if (page_contents.toLowerCase().contains("<!DOCTYPE html>".toLowerCase())) {
                    isHtmlPage = true; 
                } else {
                    System.err.println(page + " is not a valid html page");
                }
                reader.close();
                conn.disconnect();
            } catch(Exception e) {
                System.err.println(e);
                if (conn != null) {
                    conn.disconnect();
                }
            }
            
            if (isHtmlPage) {
                // .dhp stands for Decorated Html File, which is a file type I'm making up.
                File page_file = new File(page_save_location.toString() + "/" + pageUrlHash + ".dhp"); // save inside page_save_location
                try { 
                    PrintWriter writer = new PrintWriter(page_file); // write the page contents to this file.
                    // write some meta data on the top.
                    writer.println("source_url:" + page.toString());
                    writer.println("%%"); // break line.
                    writer.println(page_contents);
                    writer.close();
                    loaded_pages.put(pageUrlHash, page_file);
                    System.out.println("Write new page file: " + pageUrlHash);
                } catch(Exception e) {
                    System.err.println("Failed to write local file for page: " + e);
                }
            }
        }
        
        // go through the page to find all of the links 
        String linkPrefix = "href=\"";
        int href_find = page_contents.indexOf(linkPrefix);
        while (href_find > 0) {
            int double_quote_find = page_contents.indexOf('"', href_find + linkPrefix.length() + 1); // skip the found string
            String foundLink = page_contents.substring(href_find + linkPrefix.length(), double_quote_find);  
            boolean endsWithExcludedSuffix = false;
            for (String suf : excluded_suffixes) {
                if (foundLink.toLowerCase().trim().endsWith(suf)) {
                    endsWithExcludedSuffix = true;
                    System.err.println("The url '" + (foundLink) + "' ends with an excluded suffix");
                }
            }
            if (!endsWithExcludedSuffix) { 
                try {
                    URL foundUrl = new URL(foundLink); // attempt to add this url.
                    page_links.add(foundUrl); 
                } catch(MalformedURLException mue) {
                    System.err.println("Bad url format: " + mue);
                    String strippedLink = foundLink;
                    if (strippedLink.startsWith("//")) {
                        strippedLink = strippedLink.substring(2);
                    } else if (strippedLink.startsWith("/")) {
                        strippedLink = strippedLink.substring(1);
                    } 
                    try {
                        URL foundUrlModified = new URL(page.getProtocol() + "://" + strippedLink);
                        page_links.add(foundUrlModified); 
                        System.out.println("Added with adding a protocol '" + (foundUrlModified) + "'");
                    } catch(Exception e) { 
                        System.err.println("Still bad url with adding base, trying entire hostname.");
                        try { // try to fix the bad format with adding on a host
                            URL foundUrlModified = new URL(baseUrlStr + foundLink);
                            page_links.add(foundUrlModified);  
                            System.out.println("Added with adding a protocol and hostname '" + (foundUrlModified) + "'");
                        } catch(MalformedURLException secondMue) { 
                            System.err.println("Unable to convert this link to a proper URL."); 
                        }
                    }
                }
            }
            // update to the next potential link
            href_find = page_contents.indexOf("href=\"", href_find + 1);
        } 
        return page_links;
    }
    
    public boolean isQueueEmpty() {
        return to_explore.size() == 0;
    }
    
    public void processFrontQueue() { 
        URL front_url = to_explore.remove(0);
        ArrayList<URL> front_url_list = getUrlsInPage(front_url);
        to_explore.addAll(front_url_list); 
    }

    public int getQueueSize() {
        return to_explore.size();
    }
}
