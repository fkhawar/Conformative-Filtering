package org.latlab.learner;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import org.latlab.model.BayesNet;
import org.latlab.model.LTM;
import org.latlab.reasoner.CliqueTreePropagationRec;

/**
 * Used to hold a group of the clique tree propagation on the same model.
 * 
 * @author kmpoon
 * 
 */
public class CliqueTreePropagationGroupRec {
	private final BlockingQueue<CliqueTreePropagationRec> queue;
	public final BayesNet model;
	public final int capacity;

	public static CliqueTreePropagationGroupRec constructFromTemplate(
			CliqueTreePropagationRec template, BayesNet model, int capacity) {
		CliqueTreePropagationGroupRec group =
				new CliqueTreePropagationGroupRec(model, capacity);

		while (group.queue.size() < capacity) {
			CliqueTreePropagationRec ctp = template.clone();
			ctp.setBayesNet(model);
			group.queue.add(ctp);
		}

		return group;
	}

	public static CliqueTreePropagationGroupRec constructFromModel(BayesNet model,
			int capacity) {
		return new CliqueTreePropagationGroupRec(construct(model), capacity);
	}

	private CliqueTreePropagationGroupRec(BayesNet model, int capacity) {
		this.capacity = capacity;
		this.model = model;
		queue = new ArrayBlockingQueue<CliqueTreePropagationRec>(capacity);
	}

	public CliqueTreePropagationGroupRec(CliqueTreePropagationRec ctp, int capacity) {
		this(ctp.getBayesNet(), capacity);

		queue.add(ctp);

		while (queue.size() < capacity)
			queue.add(construct(model));
	}

	private static CliqueTreePropagationRec construct(BayesNet model) {
		if (model instanceof LTM)
			return new CliqueTreePropagationRec((LTM) model);
		else
			return new CliqueTreePropagationRec(model);
	}

	/**
	 * It constructs new clique tree propagation if necessary, otherwise reuses
	 * the ones in reserve.
	 * 
	 * @return
	 */
	public CliqueTreePropagationRec take() {
		try {
			return queue.take();
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Puts back a clique tree propagation in reserve after use.
	 * 
	 * @param ctp
	 */
	public void put(CliqueTreePropagationRec ctp) {
		try {
			queue.put(ctp);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}
}
