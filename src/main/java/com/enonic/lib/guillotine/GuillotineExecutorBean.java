package com.enonic.lib.guillotine;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;

import com.enonic.xp.script.ScriptValue;
import com.enonic.xp.script.bean.BeanContext;
import com.enonic.xp.script.bean.ScriptBean;

/**
 * Executes GraphQL queries against the Guillotine engine exposed by {@code app-guillotine} without going over the HTTP
 * wire and without any compile-time dependency on that application.
 * <p>
 * {@code app-guillotine} registers its executor as a plain {@link Function} OSGi service (the interface comes from
 * {@code java.base}, so no shared package is needed) selected by the service property
 * {@code (function=guillotine.execute)}. The contract is:
 * <pre>
 *   input  = { "query": String, "variables": Map&lt;String,Object&gt; }
 *   output = { "data": ..., "errors": ... }   // GraphQL-spec response
 * </pre>
 * The lookup is done dynamically through this bundle's own {@link BundleContext}; the OSGi service registry is global so
 * the service is found regardless of which bundle registered it. The query runs in the caller's current XP context.
 */
public final class GuillotineExecutorBean
    implements ScriptBean
{
    private static final String QUERY_KEY = "query";

    private static final String VARIABLES_KEY = "variables";

    private static final String EXECUTOR_FILTER = "(function=guillotine.execute)";

    private BundleContext bundleContext;

    @Override
    public void initialize( final BeanContext context )
    {
        final Bundle bundle = FrameworkUtil.getBundle( getClass() );
        this.bundleContext = bundle != null ? bundle.getBundleContext() : null;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> execute( final String query, final ScriptValue variables )
    {
        final Map<String, Object> input =
            Map.of( QUERY_KEY, query, VARIABLES_KEY, variables != null && variables.isObject() ? variables.getMap() : Map.of() );

        final ServiceReference<Function> reference = findExecutorReference();

        final Function<Map<String, Object>, Map<String, Object>> executor =
            (Function<Map<String, Object>, Map<String, Object>>) bundleContext.getService( reference );

        if ( executor == null )
        {
            throw new IllegalStateException( "Guillotine GraphQL executor service is unavailable - is app-guillotine started?" );
        }

        try
        {
            return executor.apply( input );
        }
        finally
        {
            bundleContext.ungetService( reference );
        }
    }

    private ServiceReference<Function> findExecutorReference()
    {
        if ( bundleContext == null )
        {
            throw new IllegalStateException( "Cannot access the OSGi BundleContext of the lib-guillotine bundle" );
        }

        final Collection<ServiceReference<Function>> references;
        try
        {
            references = bundleContext.getServiceReferences( Function.class, EXECUTOR_FILTER );
        }
        catch ( InvalidSyntaxException e )
        {
            throw new RuntimeException( e );
        }

        return references.stream().filter( ref -> {
            final Bundle bundle = ref.getBundle();
            return bundle != null && bundle.getState() == Bundle.ACTIVE;
        } ).findFirst().orElseThrow(
            () -> new IllegalStateException( "Guillotine GraphQL executor service not found - is app-guillotine installed and started?" ) );
    }

    void setBundleContext( final BundleContext bundleContext )
    {
        this.bundleContext = bundleContext;
    }
}
