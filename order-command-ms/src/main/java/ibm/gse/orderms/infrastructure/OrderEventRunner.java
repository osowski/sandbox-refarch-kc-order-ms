package ibm.gse.orderms.infrastructure;

import java.util.List;

import org.apache.kafka.common.KafkaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ibm.gse.orderms.infrastructure.events.OrderEvent;
import ibm.gse.orderms.infrastructure.kafka.OrderEventAgent;

public class OrderEventRunner implements Runnable {

	static final Logger logger = LoggerFactory.getLogger(OrderEventRunner.class);
	
	private volatile boolean running = true;
	
	@Override
	public void run() {
		logger.info("Order event consumer loop thread started");
		OrderEventAgent orderEventAgent = new OrderEventAgent();
        boolean ok = true;
        try {
            while (running && ok) {
                try {
                    List<OrderEvent> events = orderEventAgent.poll();
                    for (OrderEvent event : events) {
                    	orderEventAgent.handle(event);
                    }
                } catch (KafkaException ke) {
                    // Treat a Kafka exception as unrecoverable
                    // stop this task and queue a new one
                    ok = false;
                }
            }
        } finally {
        	orderEventAgent.safeClose();  
        }
		
	}
	
	public void stop() {
		this.running = false;
	}

}