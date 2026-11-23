package com.enonic.lib.guillotine;

import java.util.Map;

import com.enonic.xp.script.serializer.MapGenerator;
import com.enonic.xp.script.serializer.MapSerializable;

/**
 * Exposes a GraphQL response ({@code Map} of {@code data}/{@code errors} with nested maps, lists, scalars and nulls)
 * to JavaScript as a native object. A plain {@link Map} returned from a bean is handed to Nashorn as a Java object:
 * properties can be read but {@code JSON.stringify} yields {@code undefined}. {@code __.toNativeObject} only converts
 * {@link MapSerializable}, and {@link MapGenerator#value(String, Object)} already walks nested maps and lists.
 */
final class GuillotineResponseMapper
    implements MapSerializable
{
    private final Map<String, Object> response;

    GuillotineResponseMapper( final Map<String, Object> response )
    {
        this.response = response;
    }

    @Override
    public void serialize( final MapGenerator gen )
    {
        if ( response != null )
        {
            response.forEach( gen::value );
        }
    }
}
