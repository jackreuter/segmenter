//mostly copied from mallet example code
//trains hmms on input file

import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.util.zip.*;

import cc.mallet.fst.*;
import cc.mallet.pipe.*;
import cc.mallet.pipe.iterator.*;
import cc.mallet.pipe.tsf.*;
import cc.mallet.types.*;
import cc.mallet.util.*;

public class TrainHMM {

    //takes training file as first argument
    //right now set up to train two hmms, hmm1 and hmm2 and write them to file
    //hmm2 is set to be of degree 2
    public static void main(String[] args) throws Exception {
        ArrayList<Pipe> pipes = new ArrayList<Pipe>();
        pipes.add(new SimpleTaggerSentence2TokenSequence());
        pipes.add(new TokenSequence2FeatureSequence());
        Pipe pipe = new SerialPipes(pipes);

        InstanceList trainingInstances = fileToInstanceList(args[0], pipe);
        HMM hmm1 = TrainHMM(trainingInstances, pipe, 1);
        HMM hmm2 = TrainHMM(trainingInstances, pipe, 2);

        FileOutputStream fos1 = new FileOutputStream("hmm1");
        ObjectOutputStream oos1 = new ObjectOutputStream(fos1);
        oos1.writeObject(hmm1);
        oos1.writeObject(hmm1.getInitialMultinomial());

        FileOutputStream fos2 = new FileOutputStream("hmm2");
        ObjectOutputStream oos2 = new ObjectOutputStream(fos2);
        oos2.writeObject(hmm2);
        oos2.writeObject(hmm2.getInitialMultinomial());
        
    }

    //mallet file handling stuff
    public static InstanceList fileToInstanceList(String filename, Pipe pipe) throws IOException {
        InstanceList instances = new InstanceList(pipe);
        instances.addThruPipe(new LineGroupIterator(new BufferedReader(new InputStreamReader(new FileInputStream(filename))), Pattern.compile("^\\s*$"), true));
        return instances;
    }

    //trains HMM on input: training instances, input pipe, desired order
    public static HMM TrainHMM(InstanceList trainingInstances, Pipe pipe, int order) throws IOException {
        HMM hmm = new HMM(pipe, null);
        String startName =
            hmm.addOrderNStates(trainingInstances, new int[]{order}, null,
                                "O", Pattern.compile("\\s"), Pattern.compile(".*"),
                                true);
        HMMTrainerByLikelihood trainer = 
            new HMMTrainerByLikelihood(hmm);
        trainer.train(trainingInstances, 10);
        return hmm;
    }
    
}
