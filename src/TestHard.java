//tests on 231 "difficult" hashtags, i.e. those not handled by simple heuristics

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Arrays;
import java.time.Instant;
import java.time.Duration;

public class TestHard {

    private static HelperFunctions hf = new HelperFunctions();
    
    public static void main(String[] args) throws Exception {
        BufferedReader supReader = new BufferedReader(new FileReader("../data/hashtags/hardsup"));
        BufferedReader segReader = new BufferedReader(new FileReader("../data/hashtags/hardseg"));

        String[] proposed = new String[231];
        String[] correct = new String[231];

        //current optimal values
        double L = 7.0;
        double T = 3.0;
        double H = 0.0;
        
        if (args.length>0){L=Double.parseDouble(args[0]);}
        if (args.length>1){T=Double.parseDouble(args[1]);}
        if (args.length>2){H=Double.parseDouble(args[2]);}

        Segmenter s = new Segmenter("../data/dictionaries/total","../data/dictionaries/segmenter/translations","../data/dictionaries/twograms",L,T,H);

        int segmented = 0;
        String supline = supReader.readLine();
        String segline = segReader.readLine();
        Instant before = Instant.now();
        while (supline != null && segline != null) {
            //comment out to stop progress printing
            System.out.print(supline+(" "+new String(new char[50-supline.length()]).replace("\0", " ")));
            System.out.print("%"+segmented/(double)231*100+"\r");

            String seg = s.segmentByPipeline(supline);
            proposed[segmented] = seg;
            correct[segmented] = segline;
            segmented++;
            supline = supReader.readLine();
            segline = segReader.readLine();            
        }
        Instant after = Instant.now();
        Duration duration = Duration.between(before,after);
        double seconds = duration.toNanos()/1000000000.0;

        supReader.close();
        segReader.close();

        Evaluator evaluator = new Evaluator(proposed,correct);
        evaluator.evaluate();
        System.out.println(segmented+" hashtags segmented in "+seconds+"seconds");

        //add probable unknowns to dictionary based on charhmm scoring
        //should be manually checked for errors periodically for best results
        //errors should be added to improbable list to avoid repetition
        s.addProbableToDict();
    }

}
