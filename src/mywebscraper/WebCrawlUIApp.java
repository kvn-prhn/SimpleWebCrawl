
package mywebscraper;

import javafx.geometry.Insets;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javafx.application.Application;
import javafx.event.ActionEvent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField; 
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Stage;

/**
 *
 * @author Kevin
 */
public class WebCrawlUIApp extends Application {

    public volatile URL start_url;
    public volatile Path pages_path;
    public volatile int max_iters;
    private volatile boolean in_process = false;
    
    private volatile TextArea statusOutput;
    private TextField startUrlTextField;
    private TextField pathTextField;
    private TextField iterationsTextField;
    private Button startButton;
    private Button cancelButton; 
    
    class CrawlProcessThread implements Runnable {

        private WebCrawlUIApp callingApp;
        
        public CrawlProcessThread(WebCrawlUIApp app) {
            callingApp = app;
        }
        
        @Override
        public void run() {
            System.out.println("Run()");
            try {
                callingApp.outputAppStatusText("Loading local page files.");
                WebCrawlLocalFiles crawler = new WebCrawlLocalFiles(callingApp.pages_path, callingApp.start_url);
                int iters = 0; 
                callingApp.outputAppStatusText("Process started.");
                System.out.println("process started");
                System.out.println("queue size: " + crawler.getQueueSize());
                System.out.println("in_process: " + callingApp.in_process);
                System.out.println("max_iters: " + callingApp.max_iters);
                while (callingApp.in_process && iters < callingApp.max_iters && !crawler.isQueueEmpty()) {
                    crawler.processFrontQueue();
                    iters++;
                }  
            } catch(Exception e) {
                System.err.println(e);
            }
            System.out.println("Run ending");
            callingApp.completeProcess();
        }
        
    }
    
    public void launchProcess() { 
        // validate input elements.
        boolean validated = true;
        
        String oldUrlStr = start_url.toString();
        try {
            URL new_start_url = new URL(startUrlTextField.getText());
            String tryHost = new_start_url.getHost();
            String tryProtocol = new_start_url.getProtocol();
            start_url = new_start_url; // if the creation did not cause an exception, set it as the new URL.
            startUrlTextField.setText(start_url.toString()); // set it to the standard format.
        } catch(Exception ex) {
            outputAppStatusText("Invalid URL: " + ex);
            startUrlTextField.setText(oldUrlStr);
            validated = false;
        }
        
        String oldPathText = pages_path.toString();
        try {
            Path newPath = Paths.get(pathTextField.getText()).toAbsolutePath();
            if (!Files.isDirectory(newPath)) {
                Files.createDirectory(newPath);
            }
            pages_path = newPath; // verified
            pathTextField.setText(pages_path.toString()); // set to be consistent.
        } catch(Exception ex) {
            outputAppStatusText("Invalid pages path: " + ex);
            pathTextField.setText(oldPathText);
            validated = false;
        }
        
        String oldIterText = max_iters + "";
        try {
            int new_max_iters = Integer.parseInt(iterationsTextField.getText());
            if (new_max_iters < 1) {
                throw new Exception(); // invalid if its 0 or less.
            }
            max_iters = new_max_iters;
            iterationsTextField.setText(max_iters + "");
        } catch(Exception ex) {
            outputAppStatusText("Invalid max iterators value: " + ex);
            iterationsTextField.setText(oldIterText);
            validated = false;
        }
        
        if (validated) {
            in_process = true; 
            startButton.setDisable(true); // can't start when in process
            cancelButton.setDisable(false);
            Thread t = new Thread(new CrawlProcessThread(this));
            System.out.println("About the start the thread");
            t.start();
        }
    }
    
    // this is called when the crawler process ends. 
    public void completeProcess() {
        in_process = false;
        startButton.setDisable(false); 
        cancelButton.setDisable(true);
        statusOutput.appendText("\nProcess ended.");
    }
    
    @Override
    public void init() throws Exception {
        super.init();
        max_iters = 300; 
        start_url = new URL("http://www.foxnews.com/");
        pages_path = Paths.get("pages").toAbsolutePath();
    }
    
    @Override
    public void start(Stage primaryStage) throws Exception {
        BorderPane rootPane = new BorderPane();
        GridPane gridPane = new GridPane();
        VBox vbox = new VBox();
        statusOutput = new TextArea();
        statusOutput.setText("No process.");
        statusOutput.setEditable(false); 
        
        Text startUrlLabel = new Text("Start URL:");
        startUrlTextField = new TextField();
        if (start_url != null) {
            startUrlTextField.setText(start_url.toString());
        } 
        
        Text pathLabel = new Text("Pages location:");
        pathTextField = new TextField();
        if (pages_path != null) {
            pathTextField.setText(pages_path.toString());
        }
        
        Text iterationsLabel = new Text("Max Iterations:");
        iterationsTextField = new TextField();
        iterationsTextField.setText("" + max_iters);
        
        startButton = new Button("Start Collection");
        startButton.addEventHandler(ActionEvent.ACTION, (e) -> {
            if (!in_process) {
                System.out.println("Start");
                outputAppStatusText("Start button pressed");
                launchProcess();
            }
        });
        cancelButton = new Button("Cancel Process");
        cancelButton.addEventHandler(ActionEvent.ACTION, (e) -> {
            System.out.println("Cancel");
            outputAppStatusText("Cancel button pressed");
            in_process = false;
        });
        
        ColumnConstraints column1 = new ColumnConstraints();
        column1.setPercentWidth(20);
        ColumnConstraints column2 = new ColumnConstraints();
        column2.setPercentWidth(80);
        gridPane.getColumnConstraints().addAll(column1, column2); 
        gridPane.add(startUrlLabel, 0, 0);
        gridPane.add(startUrlTextField, 1, 0);
        gridPane.add(pathLabel, 0, 1);
        gridPane.add(pathTextField, 1, 1);
        gridPane.add(iterationsLabel, 0, 2);
        gridPane.add(iterationsTextField, 1, 2);
        
        vbox.getChildren().add(startButton);
        vbox.getChildren().add(cancelButton);
        
        rootPane.setPadding(new Insets(8));
        rootPane.setTop(gridPane);
        rootPane.setCenter(vbox);
        rootPane.setBottom(statusOutput);
        
        Scene scene = new Scene(rootPane, 640, 320);
        primaryStage.setResizable(false);
        primaryStage.setTitle("My Web Crawler Application");
        primaryStage.setScene(scene);
        primaryStage.show();
    }
    
    public void outputAppStatusText(String message) {
        statusOutput.appendText("\n" + message); 
    }
    
    public static void main(String[] args) {
        launch(args); 
    }
}
