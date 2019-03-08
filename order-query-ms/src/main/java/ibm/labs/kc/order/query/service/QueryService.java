package ibm.labs.kc.order.query.service;

import java.util.Collection;
import java.util.Optional;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

import ibm.labs.kc.order.query.complex.ComplexQueryDAO;
import ibm.labs.kc.order.query.complex.ComplexQueryDAOImpl;
import ibm.labs.kc.order.query.complex.ComplexQueryOrder;
import ibm.labs.kc.order.query.dao.OrderDAO;
import ibm.labs.kc.order.query.dao.OrderDAOMock;
import ibm.labs.kc.order.query.dao.QueryOrder;
import ibm.labs.kc.order.query.model.Cancellation;
import ibm.labs.kc.order.query.model.ContainerAssignment;
import ibm.labs.kc.order.query.model.Order;
import ibm.labs.kc.order.query.model.Rejection;
import ibm.labs.kc.order.query.model.VoyageAssignment;
import ibm.labs.kc.order.query.model.events.AssignContainerEvent;
import ibm.labs.kc.order.query.model.events.AssignOrderEvent;
import ibm.labs.kc.order.query.model.events.CancelOrderEvent;
import ibm.labs.kc.order.query.model.events.ContainerDeliveredEvent;
import ibm.labs.kc.order.query.model.events.ContainerOffShipEvent;
import ibm.labs.kc.order.query.model.events.ContainerOnShipEvent;
import ibm.labs.kc.order.query.model.events.CreateOrderEvent;
import ibm.labs.kc.order.query.model.events.Event;
import ibm.labs.kc.order.query.model.events.EventListener;
import ibm.labs.kc.order.query.model.events.OrderCompletedEvent;
import ibm.labs.kc.order.query.model.events.OrderEvent;
import ibm.labs.kc.order.query.model.events.RejectOrderEvent;
import ibm.labs.kc.order.query.model.events.UpdateOrderEvent;

@Path("orders")
public class QueryService implements EventListener {
    static final Logger logger = LoggerFactory.getLogger(QueryService.class);

    private OrderDAO orderDAO;
    
    private ComplexQueryDAO complexQueryOrderDAO;

    public QueryService() {
        orderDAO = OrderDAOMock.instance();
        complexQueryOrderDAO = ComplexQueryDAOImpl.instance();
    }

    @GET
    @Path("{Id}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Query an order by id", description = "")
    @APIResponses(value = {
            @APIResponse(responseCode = "404", description = "Order not found", content = @Content(mediaType = "text/plain")),
            @APIResponse(responseCode = "200", description = "Order found", content = @Content(mediaType = "application/json")) })
    public Response getById(@PathParam("Id") String orderId) {
        logger.info("QueryService.getById(" + orderId + ")");

        Optional<QueryOrder> oo = orderDAO.getById(orderId);
        if (oo.isPresent()) {
            return Response.ok().entity(oo.get()).build();
        } else {
            return Response.status(Status.NOT_FOUND).build();
        }
    }

    @GET
    @Path("byManuf/{manuf}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Query orders by manuf", description = "")
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "Orders found", content = @Content(mediaType = "application/json")) })
    public Response getByManuf(@PathParam("manuf") String manuf) {
        logger.info("QueryService.getByManuf(" + manuf + ")");

        Collection<QueryOrder> orders = orderDAO.getByManuf(manuf);
        return Response.ok().entity(orders).build();
    }

    @GET
    @Path("byStatus/{status}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Query orders by status", description = "")
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "Orders found", content = @Content(mediaType = "application/json")) })
    public Response getByStatus(@PathParam("status") String status) {
        logger.info("QueryService.getByStatus(" + status + ")");

        Collection<QueryOrder> orders = orderDAO.getByStatus(status);
        return Response.ok().entity(orders).build();
    }

    @Override
    public void handle(Event event) {
        String orderID;
        Optional<QueryOrder> oqo;
        try {
            OrderEvent orderEvent = (OrderEvent) event;
            System.out.println("@@@@ in handle " + new Gson().toJson(orderEvent));
            switch (orderEvent.getType()) {
            case OrderEvent.TYPE_CREATED:
                synchronized (orderDAO) {
                    Order o1 = ((CreateOrderEvent) orderEvent).getPayload();
                    long timestampMillis = ((CreateOrderEvent) orderEvent).getTimestampMillis();
                    String action = ((CreateOrderEvent) orderEvent).getType();
                    QueryOrder qo = QueryOrder.newFromOrder(o1);
                    orderDAO.add(qo);
                    ComplexQueryOrder cqo = ComplexQueryOrder.newFromOrder(qo, timestampMillis, action);
                    complexQueryOrderDAO.add(cqo);
                    complexQueryOrderDAO.orderHistory(cqo);
                }
                break;
            case OrderEvent.TYPE_UPDATED:
                synchronized (orderDAO) {
                    Order o2 = ((UpdateOrderEvent) orderEvent).getPayload();
                    long timestampMillis = ((UpdateOrderEvent) orderEvent).getTimestampMillis();
                    String action = ((UpdateOrderEvent) orderEvent).getType();
                    orderID = o2.getOrderID();
                    oqo = orderDAO.getById(orderID);
                    if (oqo.isPresent()) {
                        QueryOrder qo = oqo.get();
                        qo.update(o2);
                        orderDAO.update(qo);
                        ComplexQueryOrder cqo = ComplexQueryOrder.newFromOrder(qo, timestampMillis, action);
                        complexQueryOrderDAO.update(cqo);
                        complexQueryOrderDAO.orderHistory(cqo);
                    } else {
                        throw new IllegalStateException("Cannot update - Unknown order Id " + orderID);
                    }
                }
                break;
            case OrderEvent.TYPE_ASSIGNED:
                synchronized (orderDAO) {
                    VoyageAssignment voyageAssignment = ((AssignOrderEvent) orderEvent).getPayload();
                    long timestampMillis = ((AssignOrderEvent) orderEvent).getTimestampMillis();
                    String action = ((AssignOrderEvent) orderEvent).getType();
                    orderID = voyageAssignment.getOrderID();
                    oqo = orderDAO.getById(orderID);
                    if (oqo.isPresent()) {
                        QueryOrder qo = oqo.get();
                        qo.assign(voyageAssignment);
                        orderDAO.update(qo);
                        ComplexQueryOrder cqo = ComplexQueryOrder.newFromOrder(qo, timestampMillis, action);
                        complexQueryOrderDAO.update(cqo);
                        complexQueryOrderDAO.orderHistory(cqo);
                    } else {
                        throw new IllegalStateException("Cannot update - Unknown order Id " + orderID);
                    }
                }
                break;
            case OrderEvent.TYPE_REJECTED:
                synchronized (orderDAO) {
                    Rejection rejection = ((RejectOrderEvent) orderEvent).getPayload();
                    long timestampMillis = ((RejectOrderEvent) orderEvent).getTimestampMillis();
                    String action = ((RejectOrderEvent) orderEvent).getType();
                    orderID = rejection.getOrderID();
                    oqo = orderDAO.getById(orderID);
                    if (oqo.isPresent()) {
                        QueryOrder qo = oqo.get();
                        qo.reject(rejection);
                        orderDAO.update(qo);
                        ComplexQueryOrder cqo = ComplexQueryOrder.newFromOrder(qo, timestampMillis, action);
                        complexQueryOrderDAO.update(cqo);
                        complexQueryOrderDAO.orderHistory(cqo);
                    } else {
                        throw new IllegalStateException("Cannot update - Unknown order Id " + orderID);
                    }
                }
                break;
            case OrderEvent.TYPE_CONTAINER_ALLOCATED:
                synchronized (orderDAO) {
                	ContainerAssignment container = ((AssignContainerEvent) orderEvent).getPayload();
                	long timestampMillis = ((AssignContainerEvent) orderEvent).getTimestampMillis();
                	String action = ((AssignContainerEvent) orderEvent).getType();
                    orderID = container.getOrderID();
                    oqo = orderDAO.getById(orderID);
                    if (oqo.isPresent()) {
                        QueryOrder qo = oqo.get();
                        qo.assignContainer(container);
                        orderDAO.update(qo);
                        ComplexQueryOrder cqo = ComplexQueryOrder.newFromOrder(qo, timestampMillis, action);
                        complexQueryOrderDAO.update(cqo);
                        complexQueryOrderDAO.orderHistory(cqo);
                    } else {
                        throw new IllegalStateException("Cannot update - Unknown order Id " + orderID);
                    }
                }
                break;
            case OrderEvent.TYPE_CONTAINER_ON_SHIP:
                synchronized (orderDAO) {
                	ContainerAssignment container = ((ContainerOnShipEvent) orderEvent).getPayload();
                	long timestampMillis = ((ContainerOnShipEvent) orderEvent).getTimestampMillis();
                	String action = ((ContainerOnShipEvent) orderEvent).getType();
                    orderID = container.getOrderID();
                    oqo = orderDAO.getById(orderID);
                    if (oqo.isPresent()) {
                        QueryOrder qo = oqo.get();
                        qo.containerOnShip(container);
                        orderDAO.update(qo);
                        ComplexQueryOrder cqo = ComplexQueryOrder.newFromOrder(qo, timestampMillis, action);
                        complexQueryOrderDAO.update(cqo);
                        complexQueryOrderDAO.orderHistory(cqo);
                    } else {
                        throw new IllegalStateException("Cannot update - Unknown order Id " + orderID);
                    }
                }
                break;
            case OrderEvent.TYPE_CONTAINER_OFF_SHIP:
                synchronized (orderDAO) {
                	ContainerAssignment container = ((ContainerOffShipEvent) orderEvent).getPayload();
                	long timestampMillis = ((ContainerOffShipEvent) orderEvent).getTimestampMillis();
                	String action = ((ContainerOffShipEvent) orderEvent).getType();
                    orderID = container.getOrderID();
                    oqo = orderDAO.getById(orderID);
                    if (oqo.isPresent()) {
                        QueryOrder qo = oqo.get();
                        qo.containerOffShip(container);
                        orderDAO.update(qo);
                        ComplexQueryOrder cqo = ComplexQueryOrder.newFromOrder(qo, timestampMillis, action);
                        complexQueryOrderDAO.update(cqo);
                        complexQueryOrderDAO.orderHistory(cqo);
                    } else {
                        throw new IllegalStateException("Cannot update - Unknown order Id " + orderID);
                    }
                }
                break;
            case OrderEvent.TYPE_CONTAINER_DELIVERED:
                synchronized (orderDAO) {
                	ContainerAssignment container = ((ContainerDeliveredEvent) orderEvent).getPayload();
                	long timestampMillis = ((ContainerDeliveredEvent) orderEvent).getTimestampMillis();
                	String action = ((ContainerDeliveredEvent) orderEvent).getType();
                    orderID = container.getOrderID();
                    oqo = orderDAO.getById(orderID);
                    if (oqo.isPresent()) {
                        QueryOrder qo = oqo.get();
                        qo.containerDelivered(container);
                        orderDAO.update(qo);
                        ComplexQueryOrder cqo = ComplexQueryOrder.newFromOrder(qo, timestampMillis, action);
                        complexQueryOrderDAO.update(cqo);
                        complexQueryOrderDAO.orderHistory(cqo);
                    } else {
                        throw new IllegalStateException("Cannot update - Unknown order Id " + orderID);
                    }
                }
                break;
            case OrderEvent.TYPE_CANCELLED:
                synchronized (orderDAO) {
                    Cancellation cancellation = ((CancelOrderEvent) orderEvent).getPayload();
                    long timestampMillis = ((CancelOrderEvent) orderEvent).getTimestampMillis();
                    String action = ((CancelOrderEvent) orderEvent).getType();
                    orderID = cancellation.getOrderID();
                    oqo = orderDAO.getById(orderID);
                    if (oqo.isPresent()) {
                        QueryOrder qo = oqo.get();
                        qo.cancel(cancellation);
                        orderDAO.update(qo);
                        ComplexQueryOrder cqo = ComplexQueryOrder.newFromOrder(qo, timestampMillis, action);
                        complexQueryOrderDAO.update(cqo);
                        complexQueryOrderDAO.orderHistory(cqo);
                    } else {
                        throw new IllegalStateException("Cannot update - Unknown order Id " + orderID);
                    }
                }
                break;
            case OrderEvent.TYPE_COMPLETED:
                synchronized (orderDAO) {
                    Order order = ((OrderCompletedEvent) orderEvent).getPayload();
                    long timestampMillis = ((OrderCompletedEvent) orderEvent).getTimestampMillis();
                    String action = ((OrderCompletedEvent) orderEvent).getType();
                    orderID = order.getOrderID();
                    oqo = orderDAO.getById(orderID);
                    if (oqo.isPresent()) {
                        QueryOrder qo = oqo.get();
                        qo.orderCompleted(order);
                        orderDAO.update(qo);
                        ComplexQueryOrder cqo = ComplexQueryOrder.newFromOrder(qo, timestampMillis, action);
                        complexQueryOrderDAO.update(cqo);
                        complexQueryOrderDAO.orderHistory(cqo);
                    } else {
                        throw new IllegalStateException("Cannot update - Unknown order Id " + orderID);
                    }
                }
                break;
            default:
                logger.warn("Unknown event type: " + orderEvent);
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

}
