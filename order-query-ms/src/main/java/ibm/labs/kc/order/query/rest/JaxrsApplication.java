package ibm.labs.kc.order.query.rest;

import java.util.HashSet;
import java.util.Set;

import javax.ws.rs.ApplicationPath;
import javax.ws.rs.core.Application;

@ApplicationPath("/")
public class JaxrsApplication extends Application {

//    
//    @Override
//    public Set<Class<?>> getClasses() {
//        Set<Class<?>> result = new HashSet<>();
//        result.add(HealthEndpoint.class);
//        result.add(RootEndpoint.class);
//        result.add(QueryService.class);
//        result.add(ConsumerLoop.class);
//        return result;
//    }
}
