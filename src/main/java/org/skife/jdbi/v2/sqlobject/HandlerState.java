package org.skife.jdbi.v2.sqlobject;

import org.antlr.stringtemplate.StringTemplateGroup;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Encapsulates in-memory state offered to Handler instances on
 * creation to aid in downstream caching (e.g. loading StringTemplateGroup
 * instances from the classpath).
 */
public class HandlerState {
    private final Map<Class, Object> _state = new HashMap<Class, Object>();

    @SuppressWarnings("unchecked")
    public <StateClass> StateClass getState(Class key, StateCreator<StateClass> stateCreator) {
        StateClass instance = (StateClass)_state.get(key);
        if(instance != null) return instance;
        synchronized (_state) {
            if (!_state.containsKey(key)) {
                instance = stateCreator.create();
                _state.put(key, instance);
            } else {
                instance = (StateClass)_state.get(key);
            }
        }

        return instance;
    }

    public static interface StateCreator<StateClass> {
        StateClass create();
    }
}
