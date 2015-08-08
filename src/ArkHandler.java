import java.lang.Process;
import java.lang.Runtime;
import java.io.InputStream;
import java.util.Scanner;
import java.util.Arrays;

public class ArkHandler {

    private HelperFunctions hf;
    private HMMHandler hh;
    
    public ArkHandler() throws Exception {
        hf = new HelperFunctions();
        hh = new HMMHandler("../data/hmms/arkhmm");
    }

    //tags segmentation using ARK POS tagger
    //pretty slow
    public String[] tag(String seg) throws Exception {
        hf.writeCurrentSeg(seg);
        // Run a java app in a separate system process
        Process proc = Runtime.getRuntime().exec("java -jar ark.jar current/seg");
        // Then retreive the process output
        InputStream in = proc.getInputStream();
        Scanner s = new Scanner(in).useDelimiter("\\A");
        String output = s.hasNext() ? s.next() : "";
        return output.split("\n");
    }

    //gets AH score for a given string
    //i.e. tags for POS then returns average edge weight in the HMM
    //multiplied by product of confidence for each tag
    public double ahScore(String s) throws Exception {
        String[] output = tag(s);
        String labels = "";
        double confidence = 1.0;
        for (String row : output) {
            String[] entries = row.split("\t");
        }
        double score = confidence*hh.getStateProb(labels);
        return score;
    }

}
