package nz.co.revilo.Gui;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import nz.co.revilo.App;
import nz.co.revilo.Gui.GanttChart.ExtraData;
import nz.co.revilo.Output.NewOptimalResultListener;
import nz.co.revilo.Output.ScheduleResultListener;
import nz.co.revilo.Scheduling.BnBSchedule;

import java.net.URL;
import java.util.*;

public class MainLauncherController implements Initializable, ScheduleResultListener, NewOptimalResultListener {

    App app;
    protected String _graphName;
    protected List<String> _nodeNames;
    protected List<List<Boolean>> _arcs;
    protected List<List<Integer>> _arcWeights;
    protected List<Integer> _nodeWeights;
    protected List<Integer> _nodeStarts;
    protected List<Integer> _nodeProcessor;

    private Stage thisStage;
    private GUIScheduleResult results;
    private MainLauncher ml;


    public MainLauncherController(App app, MainLauncher ml) {
        _nodeStarts = new ArrayList<>();
        this.app = app;
        this.ml = ml;

    }

    @FXML
    private Button closeBtn;

    @FXML
    private Button hideBtn;

    @FXML
    private Label processorLabel;

    @FXML
    private Label memoryLabel;

    @FXML
    private Label timeLabel;

    @FXML
    private Label branchesLabel;

    @FXML
    private Label bestLabel;

    @FXML
    private Label graphNameLabel;

    @FXML
    private Label systemLabel;

    @FXML
    private Label statusLabel;

    @FXML
    private BorderPane ganttPane;

    @FXML
    private BorderPane baseBp;

    private GanttChart<Number, String> ganttChart;

    private ArrayList<String> processorCatStr;
    private ArrayList<XYChart.Series> processorCat;

    @FXML
    private void closeRevilo(ActionEvent event) {
        Platform.exit();
        System.exit(0);
    }

    @FXML
    private void minimizeRevilo(ActionEvent event) {
        ml.getPrimaryStage().setIconified(true);
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        App.getAlgorithmManager().inform(this);
        App.getAlgorithmManager().optimalInform(this);
        results = new GUIScheduleResult();
        //set up change listeners
        results.isDoneProcessingProperty().addListener(new ChangeListener<Boolean>() {
            @Override
            public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
                Platform.runLater(() -> {
                    updateGantt();
                });
            }
        });


        processorLabel.setText(App.getExecCores() + "");
//        app.startParsing();
        Task task = new Task<Void>() {
            @Override
            public Void call() throws Exception {
                App.startParsing();
                return null;
            }
        };
        graphNameLabel.setText(App.getInputFileName()); //padding
        systemLabel.setText("PROCESSING");
        statusLabel.setText("Starting up...");
        Thread thread = new Thread(task);
        thread.start();
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                Platform.runLater(new Runnable() {
                    public void run() {
                        // calculate current used memory
                        long currentMemory = Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory();
                        memoryLabel.setText((currentMemory)  / (1024l * 1024l) + ""); //needs to be better
                        timeLabel.setText(String.format("%.2f", App.getRunningTime()));
//                        timeLabel.setText(new SimpleDateFormat("mm:ss:SS").format(new Date(App.getRunningTime())));
                        bestLabel.setText(App.getAlgorithmManager().getUpperBound() + "");
                        branchesLabel.setText(App.getAlgorithmManager().getBrokenTrees() + "");

//                        timeLabel.setText(app.getRunningTime() + "");
                    }
                });
            }
        }, 0, 33);

        createGantt();

//        System.out.println(App.getAlgorithmManager().getGraphName());
    }

    @Override
    public void finalSchedule(String graphName, List<String> nodeNames, List<List<Boolean>> arcs, List<List<Integer>> arcWeights, List<Integer> nodeWeights, List<Integer> nodeStarts, List<Integer> nodeProcessor) {
        _graphName = graphName;
        _nodeNames = nodeNames;
        _nodeWeights = nodeWeights;
        _nodeStarts = nodeStarts;
        _nodeProcessor = nodeProcessor;

        Platform.runLater(new Runnable() {
            @Override
            public void run() {
//                graphNameLabel.setText("  Processing complete! " + App.getInputFileName() + " Optimal length: " + App.getAlgorithmManager().getUpperBound()); //padding
                systemLabel.setText("COMPLETE");
                statusLabel.setText("Optimal length: " + App.getAlgorithmManager().getUpperBound() + " (Time taken: " + App.getRunningTime() + " seconds)");
            }
        });
        results.setIsDoneProcessing(true);
    }

    public void createGantt() {
        NumberAxis xAxis = new NumberAxis();
        CategoryAxis yAxis = new CategoryAxis();
        ArrayList<String> processorCatStr = new ArrayList<>();
        ArrayList<XYChart.Series> processorCat = new ArrayList<>();


        for (int i = 0; i < App.getExecCores(); i++) {
            processorCatStr.add("Processor " + i);
            processorCat.add(new XYChart.Series()); // each processor has its own series
        }

        yAxis.setCategories(FXCollections.observableArrayList(processorCatStr));
        ganttChart = new GanttChart<Number, String>(xAxis, yAxis);


        ganttPane.setCenter(ganttChart);
    }

    public void updateGantt() {
        ArrayList<String> processorCatStr = new ArrayList<>();
        ArrayList<XYChart.Series> processorCat = new ArrayList<>();

        for (int i = 0; i < App.getExecCores(); i++) {
            processorCatStr.add("Processor " + i);
            processorCat.add(new XYChart.Series()); // each processor has its own series
        }


        for (int i = 0; i < _nodeStarts.size(); i++) { //using node starts as not all nodes might be set yet
            int psr = _nodeProcessor.get(i);
            XYChart.Series psrCat = processorCat.get(psr);
//            System.out.println("Node " + i + " starts at " + _nodeStarts.get(i) + " and goes for " + _nodeWeights.get(i) + " on processor " + psr);
            psrCat.getData().add(new XYChart.Data(_nodeStarts.get(i), ("Processor " + psr), new ExtraData(_nodeWeights.get(i), "status-green")));
        }

        ganttChart.getData().clear();

        for (XYChart.Series ps : processorCat) {
            ganttChart.getData().add(ps);
        }



    }

    @Override
    public void newOptimal(BnBSchedule optimal) {
        _graphName = App.getAlgorithmManager().getGraphName();
        _nodeNames = App.getAlgorithmManager().getNodeNames();
        _nodeWeights = App.getAlgorithmManager().getNodeWeights();
        _nodeStarts = new ArrayList<>();
        _nodeProcessor = new ArrayList<>();
        for(int nodeId=0; nodeId< _nodeWeights.size(); nodeId++){
            _nodeStarts.add(optimal.getClosedNodes().get(nodeId).getA());//start times
            _nodeProcessor.add(optimal.getClosedNodes().get(nodeId).getB());//processors scheduled on
        }
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                updateGantt();
                statusLabel.setText("New optimal schedule found with length " + App.getAlgorithmManager().getUpperBound() + ".");
            }
        });

    }




}
