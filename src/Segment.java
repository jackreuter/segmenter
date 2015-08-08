// Final program to segment hashtags
// Jack Reuter 2015 REU UCCS

// input file of hashtags as first command line arg
// optionally input scoring weight ratios as 3 extra inputs, L,T,H
// corresponding segmentations are printed
// known words are in lowercase
// uknown words are all caps
// requires extra heap space, I've been using -Xmx8g

import java.io.BufferedReader;
import java.io.FileReader;
import java.time.Instant;
import java.time.Duration;

public class Segment {

    public static void main(String[] args) throws Exception {

        //current optimal values for speed & performance
        double L = 7.0;
        double T = 3.0;
        double H = 0.0;
        
        if (args.length>1){L=Double.parseDouble(args[1]);}
        if (args.length>2){T=Double.parseDouble(args[2]);}
        if (args.length>3){H=Double.parseDouble(args[3]);}
        
        Segmenter s = new Segmenter("../data/dictionaries/total","../data/dictionaries/translations","../data/dictionaries/twograms",L,T,H);
        BufferedReader br = new BufferedReader(new FileReader(args[0]));
        String line = br.readLine();
        while (line != null) {
            Instant before = Instant.now();
            String seg = s.segmentByPipeline(line);
            Instant after = Instant.now();
            Duration duration = Duration.between(before,after);
            double seconds = duration.toNanos()/1000000000.0;
            System.out.println(line+"\t"+seg+"\t"+seconds+"sec");
            line = br.readLine();
        }
        br.close();

        //add probable unknowns to dictionary based on charhmm scoring
        //should be manually checked for errors periodically for best results
        //errors should be added to improbable list to avoid repetition
        s.addProbableToDict();
    }
}
