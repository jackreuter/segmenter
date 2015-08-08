//tests on random selection of n hashtags from 1129 manually segmented
//sup refers to unsegmented file
//seg refers to segmented file, i.e. answer key
//I know, the names don't really make sense, feel free to change
//requires more heap space than typically allocated, I've been using -Xmx8g

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Random;
import java.util.Arrays;
import java.time.Instant;
import java.time.Duration;

public class TestOnRandomN {

    private static HelperFunctions hf = new HelperFunctions();

    public static void main(String[] args) throws Exception {
        BufferedReader supReader = new BufferedReader(new FileReader("../data/hashtags/1129sup.txt"));
        BufferedReader segReader = new BufferedReader(new FileReader("../data/hashtags/1129seg.txt"));
        int n = Integer.parseInt(args[0]);
        int[] indices = getIndices(n,1129);
        String[] proposed = new String[n];
        String[] correct = new String[n];

        //current optimal weights
        double L = 7.0;
        double T = 3.0;
        double H = 0.0;

        if (args.length>1){L=Double.parseDouble(args[1]);}
        if (args.length>2){T=Double.parseDouble(args[2]);}
        if (args.length>3){H=Double.parseDouble(args[3]);}

        Segmenter s = new Segmenter("../data/dictionaries/total","../data/dictionaries/translations","../data/dictionaries/twograms",L,T,H);

        int i = 0;
        int segmented = 0;
        String supline = supReader.readLine();
        String segline = segReader.readLine();
        Instant before = Instant.now();
        while (supline != null && segline != null) {
            if (hf.contains(indices,i)) {
                //comment out to get rid of progress printing
                System.out.print(supline+(" "+new String(new char[50-supline.length()]).replace("\0", " ")));
                System.out.print("%"+segmented/(double)n*100+"\r");
                
                String seg = s.segmentByPipeline(supline);
                proposed[segmented] = seg;
                correct[segmented] = segline;
                segmented++;
            }
            supline = supReader.readLine();
            segline = segReader.readLine();            
            i++;
        }
        Instant after = Instant.now();
        Duration duration = Duration.between(before,after);
        double seconds = duration.toNanos()/1000000000.0;

        supReader.close();
        segReader.close();

        Evaluator evaluator = new Evaluator(proposed,correct);
        evaluator.evaluate();
        System.out.println(n+" hashtags segmented in "+seconds+"seconds");

        //add probable unknowns to dictionary based on charhmm scoring
        //should be manually checked for errors periodically for best results
        //errors should be added to improbable list to avoid repetition
        s.addProbableToDict();
    }

    //gets random n indices
    public static int[] getIndices(int n, int lines) {
        int[] indices = new int[n];
        Random r = new Random();
        int count = 0;
        while (count<n) {
            int randomNum = r.nextInt(lines);
            if (!hf.contains(indices,randomNum)) {
                indices[count]=randomNum;
                count++;
            }
        }
        return indices;
    }

}
