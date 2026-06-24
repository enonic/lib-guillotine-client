package com.enonic.lib.guillotine;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

import com.enonic.xp.script.ScriptValue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GuillotineExecutorBeanTest
{
    private static final String FILTER = "(function=guillotine.execute)";

    private BundleContext bundleContext;

    private ServiceReference<Function> reference;

    private Function<Map<String, Object>, Map<String, Object>> executor;

    private GuillotineExecutorBean bean;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp()
    {
        bundleContext = mock( BundleContext.class );
        reference = mock( ServiceReference.class );
        executor = mock( Function.class );

        bean = new GuillotineExecutorBean();
        bean.setBundleContext( bundleContext );
    }

    @Test
    @SuppressWarnings("unchecked")
    void execute_passes_query_and_variables_and_returns_response()
        throws Exception
    {
        final Bundle bundle = mock( Bundle.class );
        when( bundle.getState() ).thenReturn( Bundle.ACTIVE );
        when( reference.getBundle() ).thenReturn( bundle );

        when( bundleContext.getServiceReferences( eq( Function.class ), eq( FILTER ) ) ).thenReturn( List.of( reference ) );
        when( bundleContext.getService( reference ) ).thenReturn( (Function) executor );

        final Map<String, Object> response = Map.of( "data", Map.of( "hello", "world" ) );
        when( executor.apply( any() ) ).thenReturn( response );

        final ScriptValue variables = mock( ScriptValue.class );
        when( variables.isObject() ).thenReturn( true );
        when( variables.getMap() ).thenReturn( Map.of( "id", "123" ) );

        final Map<String, Object> result = bean.execute( "{ guillotine }", variables );

        assertSame( response, result );

        final org.mockito.ArgumentCaptor<Map<String, Object>> captor = org.mockito.ArgumentCaptor.forClass( Map.class );
        verify( executor ).apply( captor.capture() );
        final Map<String, Object> input = captor.getValue();
        assertEquals( "{ guillotine }", input.get( "query" ) );
        assertEquals( Map.of( "id", "123" ), input.get( "variables" ) );

        verify( bundleContext ).ungetService( reference );
    }

    @Test
    @SuppressWarnings("unchecked")
    void execute_with_null_variables_uses_empty_map()
        throws Exception
    {
        final Bundle bundle = mock( Bundle.class );
        when( bundle.getState() ).thenReturn( Bundle.ACTIVE );
        when( reference.getBundle() ).thenReturn( bundle );

        when( bundleContext.getServiceReferences( eq( Function.class ), eq( FILTER ) ) ).thenReturn( List.of( reference ) );
        when( bundleContext.getService( reference ) ).thenReturn( (Function) executor );
        when( executor.apply( any() ) ).thenReturn( Map.of() );

        bean.execute( "{ guillotine }", null );

        final org.mockito.ArgumentCaptor<Map<String, Object>> captor = org.mockito.ArgumentCaptor.forClass( Map.class );
        verify( executor ).apply( captor.capture() );
        assertEquals( Map.of(), captor.getValue().get( "variables" ) );
    }

    @Test
    void execute_throws_when_service_not_found()
        throws Exception
    {
        when( bundleContext.getServiceReferences( eq( Function.class ), eq( FILTER ) ) ).thenReturn( List.of() );

        assertThrows( IllegalStateException.class, () -> bean.execute( "{ guillotine }", null ) );
    }

    @Test
    void execute_throws_when_bundle_context_unavailable()
    {
        bean.setBundleContext( null );
        assertThrows( IllegalStateException.class, () -> bean.execute( "{ guillotine }", null ) );
    }
}
