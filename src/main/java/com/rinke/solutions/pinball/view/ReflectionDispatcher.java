package com.rinke.solutions.pinball.view;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.StringUtils;

import com.rinke.solutions.beans.Autowired;
import com.rinke.solutions.beans.Bean;
import com.rinke.solutions.pinball.util.MessageUtil;
import com.rinke.solutions.pinball.view.handler.CommandHandler;
import com.rinke.solutions.pinball.view.model.ViewModel;

@Slf4j
@Bean
public class ReflectionDispatcher implements CmdDispatcher {
	
	@Autowired
	ViewModel viewModel;
	
	@Autowired
	MessageUtil messageUtil;
	
	//@Autowired XStreamUtil xStreamUtil;
	
	List<CommandHandler> handler = new ArrayList<>();

	public void registerHandler(CommandHandler o) {
		log.debug("registering handler: {}", o.getClass().getSimpleName());
		handler.add(o);
	}
	
	private static class HandlerInvocation {
		public Method m;
		public CommandHandler handler;
		public HandlerInvocation(Method m, CommandHandler handler) {
			super();
			this.m = m;
			this.handler = handler;
		}
		@Override
		public String toString() {
			return String.format("HandlerInvocation [m=%s, handler=%s]", m.getName(), handler.getClass().getSimpleName());
		}
	}
	
	Map<Command<?>,List<HandlerInvocation>> invocationCache = new HashMap<>();
	
	@Override
	public <T> void dispatch(Command<T> cmd) {
		boolean wasHandled = false;
		List<HandlerInvocation> invocationList = invocationCache.get(cmd);
		if( invocationList != null ) {
			callCachedHandlers(cmd, invocationList);
			wasHandled = true;
		} else {
			String methodName = "on"+StringUtils.capitalize(cmd.name);
			wasHandled = scanForHandlers(cmd, methodName);
		}
		if( !wasHandled ) {
			log.error("**** cmd {} was not handled", cmd);
			//throw new RuntimeException("cmd "+cmd.name+ " was not handled");
			messageUtil.error("Command not handled", "The command '"+cmd+"' was not handled. (maybe not implemented)");
		}
		//log.info( xStreamUtil.toXML(viewModel) );
	}

	<T> boolean scanForHandlers(Command<T> cmd,  String methodName) {
		boolean wasHandled = false;
		for( CommandHandler handler : handler) {
			Method[] methods = handler.getClass().getDeclaredMethods();
			for( Method m : methods) {
				if( m.getName().equals(methodName) ) {
					try {
						if( callHandler(cmd, m, handler) ) {
							addToCache(m,handler,cmd);
							wasHandled = true;
							break;
						}
					} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
						log.error("Error calling {}", m.getName(), unroll(e));
						throw new RuntimeException("error calling "+m.getName(), unroll(e));
					}
				}
			}
		}
		return wasHandled;
	}
	
	private <T> boolean callHandler(Command<T> cmd, Method m, CommandHandler handler) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		log.debug("calling {}:{}", handler.getClass().getSimpleName(), m.getName());
		if( cmd.param != null && m.getParameterCount() > 0) {
			if( m.getParameterCount()==1 ) {
				m.invoke(handler, cmd.param);	
				return true;
			} else {
				Object[] params = (Object[]) cmd.param;
				if( m.getParameterCount()==2 ) { 
					m.invoke(handler, params[0], params[1]);	
					return true;
				}
				if( m.getParameterCount()==3 ) {
					m.invoke(handler, params[0], params[1], params[2]);	
					return true;
				}
			}
		} else if( cmd.param == null && m.getParameterCount()==0) {
			m.invoke(handler);
			return true;
		}
		return false;
	}

	<T> void callCachedHandlers(Command<T> cmd, List<HandlerInvocation> invocationList) {
		for( HandlerInvocation hi : invocationList ) {
			try {
				callHandler(cmd, hi.m, hi.handler);
			} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
				log.error("Error calling {}", hi.m.getName(), unroll(e));
				throw new RuntimeException("error calling "+hi.m.getName(), unroll(e));
			}
		}
	}
	
	synchronized private <T> void addToCache(Method m, CommandHandler handler, Command<T> cmd) {
		List<HandlerInvocation> invocationList = invocationCache.get(cmd);
		if( invocationList == null ) {
			invocationList = new ArrayList<>();
			invocationCache.put(cmd, invocationList);
		}
		invocationList.add(new HandlerInvocation(m, handler));
	}

	private Throwable unroll(Throwable e) {
		return ( e instanceof InvocationTargetException ) ? ((InvocationTargetException) e).getTargetException() : e;
	}

	@Override
	public List<CommandHandler> getCommandHandlers() {
		return handler;
	}

}