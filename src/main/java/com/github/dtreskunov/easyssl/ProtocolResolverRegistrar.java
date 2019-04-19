package com.github.dtreskunov.easyssl;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.context.properties.ConfigurationPropertiesBindingPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.ProtocolResolver;
import org.springframework.util.Assert;

/**
 * A bean of this type may be used to register a custom {@link ProtocolResolver}.
 * 
 * Note that {@link BeanPostProcessor} interface is needed to make sure that the resolver
 * gets registered with the {@link ApplicationContext} <strong>before</strong> {@link
 * ConfigurationPropertiesBindingPostProcessor} runs. This way, the resolver is available
 * when {@code @ConfigurationProperties} beans get their values injected by CPBPP.
 * 
 * Refer to the logic in {@link
 * org.springframework.context.support.PostProcessorRegistrationDelegate#registerBeanPostProcessors
 * registerBeanPostProcessors}.
 */
public class ProtocolResolverRegistrar implements ApplicationContextAware, BeanPostProcessor {
	private final ProtocolResolver m_resolver;

	public ProtocolResolverRegistrar(ProtocolResolver resolver) {
		Assert.notNull(resolver, "resolver cannot be null");
		m_resolver = resolver;
	}
	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		((ConfigurableApplicationContext)applicationContext).addProtocolResolver(m_resolver);
	}

	@Override
	public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
		return bean;
	}

	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
		return bean;
	}
}