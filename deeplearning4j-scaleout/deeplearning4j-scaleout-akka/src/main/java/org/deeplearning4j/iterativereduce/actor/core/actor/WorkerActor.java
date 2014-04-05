package org.deeplearning4j.iterativereduce.actor.core.actor;

import java.util.List;
import java.util.UUID;

import org.deeplearning4j.iterativereduce.actor.core.ClearWorker;
import org.deeplearning4j.iterativereduce.tracker.statetracker.StateTracker;
import org.deeplearning4j.scaleout.conf.Conf;
import org.deeplearning4j.scaleout.conf.DeepLearningConfigurable;
import org.deeplearning4j.scaleout.iterativereduce.ComputableWorker;
import org.deeplearning4j.scaleout.iterativereduce.Updateable;

import scala.concurrent.duration.Duration;
import akka.actor.ActorRef;
import akka.actor.Address;
import akka.actor.AddressFromURIString;
import akka.actor.OneForOneStrategy;
import akka.actor.SupervisorStrategy;
import akka.actor.SupervisorStrategy.Directive;
import akka.actor.UntypedActor;
import akka.cluster.Cluster;
import akka.cluster.ClusterEvent.MemberEvent;
import akka.contrib.pattern.DistributedPubSubExtension;
import akka.contrib.pattern.DistributedPubSubMediator;
import akka.contrib.pattern.DistributedPubSubMediator.Put;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.Function;

/**
 * Baseline worker actor class
 * @author Adam Gibson
 *
 * @param <E>
 */
public abstract class WorkerActor<E extends Updateable<?>> extends UntypedActor implements DeepLearningConfigurable,ComputableWorker<E> {

	protected ActorRef mediator;
	protected E e;
	protected E results;
	protected LoggingAdapter log = Logging.getLogger(getContext().system(), this);
	protected int fineTuneEpochs;
	protected int preTrainEpochs;
	protected int[] hiddenLayerSizes;
	protected int numHidden;
	protected int numVisible;
	protected int numHiddenNeurons;
	protected int renderWeightEpochs;
	protected long seed;
	protected double learningRate;
	protected double corruptionLevel;
	protected Object[] extraParams;
	protected String id;
	protected boolean useRegularization;
	Cluster cluster = Cluster.get(getContext().system());
	protected ActorRef clusterClient;
	protected String masterPath;
	protected StateTracker<E> tracker;
	public WorkerActor(Conf conf,StateTracker<E> tracker) {
		this(conf,null,tracker);
	}

	public WorkerActor(Conf conf,ActorRef client,StateTracker<E> tracker) {
		setup(conf);

		this.tracker = tracker;

		//subscribe to broadcasts from workers (location agnostic)
		mediator.tell(new Put(getSelf()), getSelf());

		//subscribe to broadcasts from master (location agnostic)
		mediator.tell(new DistributedPubSubMediator.Subscribe(MasterActor.BROADCAST, getSelf()), getSelf());
		//subscribe to shutdown messages
		mediator.tell(new DistributedPubSubMediator.Subscribe(MasterActor.SHUTDOWN, getSelf()), getSelf());
		id = generateId();
		//replicate the network
		mediator.tell(new DistributedPubSubMediator.Publish(MasterActor.MASTER,
				register()), getSelf());

		this.clusterClient = client;
		
		//ensure worker is available to tracker
		tracker.availableForWork(id);
		//master lookup
		masterPath = conf.getMasterAbsPath();
		log.info("Registered with master " + id + " at master " + conf.getMasterAbsPath());
	}

	/**
	 * Returns a worker state with the id generated by this worker
	 * @return a worker state with the id of this worker
	 */
	public WorkerState register() {
		return new WorkerState(this.id);
	}

	/**
	 * Generates an id for this worker
	 * @return a UUID for this worker
	 */
	public String generateId() {
		return UUID.randomUUID().toString();
    }


	@Override
	public void postStop() throws Exception {
		super.postStop();
		try {
			tracker.removeWorker(id);

		}catch(Exception e) {
			log.info("Tracker already shut down");
		}
		log.info("Post stop on worker actor");
		cluster.unsubscribe(getSelf());
	}

	@Override
	public void preStart() throws Exception {
		super.preStart();
		cluster.subscribe(getSelf(), MemberEvent.class);
		log.info("Pre start on worker");

	}



	@Override
	public E compute(List<E> records) {
		return compute();
	}

	@Override
	public abstract E compute();

	@Override
	public boolean incrementIteration() {
		return false;
	}

	@Override
	public void setup(Conf conf) {
		hiddenLayerSizes = conf.getLayerSizes();
		numHidden = conf.getnOut();
		numVisible = conf.getnIn();
		numHiddenNeurons = hiddenLayerSizes.length;
		seed = conf.getSeed();
		renderWeightEpochs = conf.getRenderWeightEpochs();
		useRegularization = conf.isUseRegularization();
		learningRate = conf.getPretrainLearningRate();
		preTrainEpochs = conf.getPretrainEpochs();
		fineTuneEpochs = conf.getFinetuneEpochs();
		corruptionLevel = conf.getCorruptionLevel();
		extraParams = conf.getDeepLearningParams();
		String url = conf.getMasterUrl();
		this.masterPath = conf.getMasterAbsPath();
		Address a = AddressFromURIString.apply(url);
		Cluster.get(context().system()).join(a);

		mediator = DistributedPubSubExtension.get(getContext().system()).mediator();

	}



	@Override
	public SupervisorStrategy supervisorStrategy() {
		return new OneForOneStrategy(0, Duration.Zero(),
				new Function<Throwable, Directive>() {
			public Directive apply(Throwable cause) {
				log.error("Problem with processing",cause);
				mediator.tell(new DistributedPubSubMediator.Publish(MasterActor.MASTER,
						new ClearWorker(id)), getSelf());


				return SupervisorStrategy.restart();
			}
		});
	}



	@Override
	public E getResults() {
		return results;
	}

	@Override
	public void update(E t) {
		this.e = t;
	}




	public  E getE() {
		return e;
	}




	public  void setE(E e) {
		this.e = e;
	}





}
