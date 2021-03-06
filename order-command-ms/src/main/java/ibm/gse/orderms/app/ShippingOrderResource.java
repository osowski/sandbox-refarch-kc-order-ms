package ibm.gse.orderms.app;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
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

import ibm.gse.orderms.app.dto.ShippingOrderCreateParameters;
import ibm.gse.orderms.app.dto.ShippingOrderReference;
import ibm.gse.orderms.app.dto.ShippingOrderUpdateParameters;
import ibm.gse.orderms.domain.model.order.ShippingOrder;
import ibm.gse.orderms.domain.service.ShippingOrderService;

/**
 * Expose the commands and APIs used by external clients
 * 
 * @author jerome boyer
 *
 */
@Path("orders")
public class ShippingOrderResource {
	static final Logger logger = LoggerFactory.getLogger(ShippingOrderResource.class);
	
	@Inject
	public ShippingOrderService shippingOrderService;

	public ShippingOrderResource() {	
	}
	
	public ShippingOrderResource(ShippingOrderService shippingOrderService) {
		this.shippingOrderService = shippingOrderService;
	}

	@POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Request to create an order", description = "")
    @APIResponses(value = {
            @APIResponse(responseCode = "400", description = "Bad create order request", content = @Content(mediaType = "text/plain")),
            @APIResponse(responseCode = "200", description = "Order created, return order unique identifier", content = @Content(mediaType = "text/plain")) })
	public Response createShippingOrder(ShippingOrderCreateParameters orderParameters) {
		if (orderParameters == null ) {
			return Response.status(400, "No parameter sent").build();
		}
		try {
		   ShippingOrderCreateParameters.validateInputData(orderParameters);
		 
		} catch(IllegalArgumentException iae) {
			return Response.status(400, iae.getMessage()).build();
		}
		ShippingOrder order = ShippingOrderFactory.createNewShippingOrder(orderParameters);
		try {
			shippingOrderService.createOrder(order);
		} catch(Exception e) {
			return Response.serverError().build();
		}
	    return Response.ok().entity(order.getOrderID()).build();
	}
	


    @PUT
    @Path("{Id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Request to update an order", description = "")
    @APIResponses(value = {
            @APIResponse(responseCode = "404", description = "Unknown order ID", content = @Content(mediaType = "text/plain")),
            @APIResponse(responseCode = "400", description = "Bad update order request", content = @Content(mediaType = "text/plain")),
            @APIResponse(responseCode = "200", description = "Order updated", content = @Content(mediaType = "text/plain")) })
    /**
     * Update an existing shipping order given an existing identifier and the update parameters
     * @param orderID
     * @param orderParameters
     * @return 
     */
    public Response updateExistingOrder(@PathParam("Id") String orderID, ShippingOrderUpdateParameters orderParameters) {
    	   logger.info("updateExistingOrder: " + orderID);

    	if (orderParameters == null ) {
			return Response.status(Status.BAD_REQUEST).build();
		}
    	
        if(! Objects.equals(orderID, orderParameters.getOrderID())) {
        	logger.error(orderID + " does not match " + orderParameters.getOrderID());
            return Response.status(Status.BAD_REQUEST).build();
        }

        Optional<ShippingOrder> existingOrder = shippingOrderService.getOrderByOrderID(orderID);
        if (existingOrder.isPresent()) {
        	try {
	            ShippingOrderUpdateParameters.validate(orderParameters, existingOrder.get());
	            logger.info("@@@ ");
	            ShippingOrder updatedOrder = new ShippingOrder(orderID,
	                    orderParameters.getProductID(),
	                    orderParameters.getCustomerID(),
	                    orderParameters.getQuantity(),
	                    orderParameters.getPickupAddress(), orderParameters.getPickupDate(),
	                    orderParameters.getDestinationAddress(), orderParameters.getExpectedDeliveryDate(),
	                    orderParameters.getStatus());
		            try {
		            	shippingOrderService.updateShippingOrder(updatedOrder);
		            	logger.info("@@@@ ");
		            } catch (Exception e) {
		                logger.error("Fail to publish order updated event", e);
		                return Response.serverError().build();
		            }
        	 } catch (Exception e) {
                 logger.error("Error in payload", e);
                 return Response.serverError().build();
             }    
            return Response.ok().build();
        } else {
        	logger.error(orderID + " not found ");
            return Response.status(Status.NOT_FOUND).build();
        }
    }
    
	
	@GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Returns order references with order_id, customer_id and product_id", description = "")
    @APIResponses(value = {
            @APIResponse(responseCode = "404", description = "No records found", content = @Content(mediaType = "text/plain")),
            @APIResponse(responseCode = "200", description = "OK", content = @Content(mediaType = "application/json")) })
    public Response getAllOrderReferences() {
        logger.info("OrderAdminService.getAllOrderReferences()");

        Collection<ShippingOrderReference> orderList = shippingOrderService.getOrderReferences();
        if (! orderList.isEmpty()) {
        	return Response.ok().entity(orderList).build();	
        } else {
        	 return Response.status(Status.NOT_FOUND).build();
             	
        }
	}


    @GET
    @Path("{Id}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Query an order by id", description = "")
    @APIResponses(value = {
            @APIResponse(responseCode = "404", description = "Order not found", content = @Content(mediaType = "text/plain")),
            @APIResponse(responseCode = "200", description = "Order found", content = @Content(mediaType = "application/json")) })
    public Response getOrderByOrderId(@PathParam("Id") String orderId) {
        logger.info("QueryService.getOrderByOrderId(" + orderId + ")");

        Optional<ShippingOrder> oo = shippingOrderService.getOrderByOrderID(orderId);
        if (oo.isPresent()) {
            return Response.ok().entity(oo.get()).build();
        } else {
            return Response.status(Status.NOT_FOUND).build();
        }
    }
}
