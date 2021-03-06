package com.yahoo.labs.samoa.tasks;

/*
 * #%L
 * SAMOA
 * %%
 * Copyright (C) 2013 Yahoo! Inc.
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
import java.text.SimpleDateFormat;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.javacliparser.ClassOption;
import com.github.javacliparser.Configurable;
import com.github.javacliparser.FileOption;
import com.github.javacliparser.IntOption;
import com.github.javacliparser.StringOption;
import com.yahoo.labs.samoa.evaluation.BasicClassificationPerformanceEvaluator;
import com.yahoo.labs.samoa.evaluation.ClassificationPerformanceEvaluator;
import com.yahoo.labs.samoa.evaluation.EvaluatorProcessor;
import com.yahoo.labs.samoa.learners.Learner;
import com.yahoo.labs.samoa.learners.classifiers.trees.VerticalHoeffdingTree;
import com.yahoo.labs.samoa.moa.streams.InstanceStream;
import com.yahoo.labs.samoa.moa.streams.generators.RandomTreeGenerator;
import com.yahoo.labs.samoa.streams.PrequentialSourceProcessor;
import com.yahoo.labs.samoa.topology.ComponentFactory;
import com.yahoo.labs.samoa.topology.Stream;
import com.yahoo.labs.samoa.topology.Topology;
import com.yahoo.labs.samoa.topology.TopologyBuilder;

/**
 * Prequential Evaluation task is a scheme in evaluating performance of online classifiers which uses each instance for testing online classifiers model and
 * then it further uses the same instance for training the model(Test-then-train)
 * 
 * @author Arinto Murdopo
 * 
 */
public class PrequentialEvaluation implements Task, Configurable {

    private static final long serialVersionUID = -8246537378371580550L;

    private static Logger logger = LoggerFactory.getLogger(PrequentialEvaluation.class);

    public ClassOption learnerOption = new ClassOption("learner", 'l', "Classifier to train.", Learner.class, VerticalHoeffdingTree.class.getName());

    public ClassOption streamTrainOption = new ClassOption("trainStream", 's', "Stream to learn from.", InstanceStream.class,
            RandomTreeGenerator.class.getName());

    public ClassOption evaluatorOption = new ClassOption("evaluator", 'e', "Classification performance evaluation method.",
            ClassificationPerformanceEvaluator.class, BasicClassificationPerformanceEvaluator.class.getName());

    public IntOption instanceLimitOption = new IntOption("instanceLimit", 'i', "Maximum number of instances to test/train on  (-1 = no limit).", 1000000, -1,
            Integer.MAX_VALUE);

    public IntOption timeLimitOption = new IntOption("timeLimit", 't', "Maximum number of seconds to test/train for (-1 = no limit).", -1, -1,
            Integer.MAX_VALUE);

    public IntOption sampleFrequencyOption = new IntOption("sampleFrequency", 'f', "How many instances between samples of the learning performance.", 100000,
            0, Integer.MAX_VALUE);

    public StringOption evaluationNameOption = new StringOption("evalutionName", 'n', "Identifier of the evaluation", "Prequential_"
            + new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()));

    public FileOption dumpFileOption = new FileOption("dumpFile", 'd', "File to append intermediate csv results to", null, "csv", true);

    private PrequentialSourceProcessor preqSource;

    // private PrequentialSourceTopologyStarter preqStarter;

    // private EntranceProcessingItem sourcePi;

    private Stream sourcePiOutputStream;

    private Learner classifier;

    private EvaluatorProcessor evaluator;

    // private ProcessingItem evaluatorPi;

    private Stream evaluatorPiInputStream;

    private Topology prequentialTopology;

    private TopologyBuilder builder;

    public void getDescription(StringBuilder sb, int indent) {
        sb.append("Prequential evaluation");
    }

    @Override
    public void init() {
        // TODO remove the if statement
        // theoretically, dynamic binding will work here!
        // test later!
        // for now, the if statement is used by Storm

        if (builder == null) {
            builder = new TopologyBuilder();
            logger.debug("Sucessfully instantiating TopologyBuilder");

            builder.initTopology(evaluationNameOption.getValue());
            logger.debug("Sucessfully initializing SAMOA topology with name {}", evaluationNameOption.getValue());
        }

        // instantiate PrequentialSourceProcessor and its output stream (sourcePiOutputStream)
        preqSource = new PrequentialSourceProcessor();
        preqSource.setStreamSource((InstanceStream) this.streamTrainOption.getValue());
        preqSource.setMaxNumInstances(instanceLimitOption.getValue());
        builder.addEntranceProcessor(preqSource);
        logger.debug("Sucessfully instantiating PrequentialSourceProcessor");

        // preqStarter = new PrequentialSourceTopologyStarter(preqSource, instanceLimitOption.getValue());
        // sourcePi = builder.createEntrancePi(preqSource, preqStarter);
        // sourcePiOutputStream = builder.createStream(sourcePi);

        sourcePiOutputStream = builder.createStream(preqSource);
        // preqStarter.setInputStream(sourcePiOutputStream);

        // instantiate classifier and connect it to sourcePiOutputStream
        classifier = (Learner) this.learnerOption.getValue();
        classifier.init(builder, preqSource.getDataset(), 1);
        builder.connectInputShuffleStream(sourcePiOutputStream, classifier.getInputProcessor());
        logger.debug("Sucessfully instantiating Classifier");

        evaluatorPiInputStream = classifier.getResultStream();
        evaluator = new EvaluatorProcessor.Builder((ClassificationPerformanceEvaluator) this.evaluatorOption.getValue())
                .samplingFrequency(sampleFrequencyOption.getValue()).dumpFile(dumpFileOption.getFile()).build();

        // evaluatorPi = builder.createPi(evaluator);
        // evaluatorPi.connectInputShuffleStream(evaluatorPiInputStream);
        builder.addProcessor(evaluator);
        builder.connectInputShuffleStream(evaluatorPiInputStream, evaluator);

        logger.debug("Sucessfully instantiating EvaluatorProcessor");

        prequentialTopology = builder.build();
        logger.debug("Sucessfully building the topology");
    }

    @Override
    public void setFactory(ComponentFactory factory) {
        // TODO unify this code with init()
        // for now, it's used by S4 App
        // dynamic binding theoretically will solve this problem
        builder = new TopologyBuilder(factory);
        logger.debug("Sucessfully instantiating TopologyBuilder");

        builder.initTopology(evaluationNameOption.getValue());
        logger.debug("Sucessfully initializing SAMOA topology with name {}", evaluationNameOption.getValue());

    }

    public Topology getTopology() {
        return prequentialTopology;
    }
    //
    // @Override
    // public TopologyStarter getTopologyStarter() {
    // return this.preqStarter;
    // }
}
