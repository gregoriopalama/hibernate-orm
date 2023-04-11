/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.id.enhanced;

import java.lang.reflect.Constructor;
import java.util.Properties;

import org.hibernate.cfg.AvailableSettings;
import org.hibernate.internal.CoreMessageLogger;

import org.jboss.logging.Logger;

import static org.hibernate.internal.util.StringHelper.isNotEmpty;

/**
 * Factory for {@link Optimizer} instances.
 *
 * @author Steve Ebersole
 */
public class OptimizerFactory {
	private static final CoreMessageLogger LOG = Logger.getMessageLogger(
			CoreMessageLogger.class,
			OptimizerFactory.class.getName()
	);

	/**
	 * Does the given optimizer name represent a pooled strategy?
	 *
	 * @param optimizerName The name of the optimizer
	 *
	 * @return {@code true} indicates the optimizer is a pooled strategy.
	 *
	 * @deprecated No longer used
	 */
	@Deprecated(since = "6.3")
	public static boolean isPooledOptimizer(String optimizerName) {
		return StandardOptimizerDescriptor.fromExternalName( optimizerName ).isPooled();
	}

	private static final Class<?>[] CTOR_SIG = new Class[] { Class.class, int.class };

	private static Optimizer buildOptimizer(OptimizerDescriptor descriptor, Class<?> returnClass, int incrementSize) {
		final Class<? extends Optimizer> optimizerClass;
		try {
			optimizerClass = descriptor.getOptimizerClass();
		}
		catch ( Throwable ignore ) {
			LOG.unableToLocateCustomOptimizerClass( descriptor.getExternalName() );
			return buildFallbackOptimizer( returnClass, incrementSize );
		}

		try {
			final Constructor<? extends Optimizer> ctor = optimizerClass.getConstructor( CTOR_SIG );
			return ctor.newInstance( returnClass, incrementSize );
		}
		catch ( Throwable ignore ) {
			LOG.unableToInstantiateOptimizer( descriptor.getExternalName() );
		}

		return buildFallbackOptimizer( returnClass, incrementSize );
	}

	private static Optimizer buildFallbackOptimizer(Class<?> returnClass, int incrementSize) {
		return new NoopOptimizer( returnClass, incrementSize );
	}

	/**
	 * Builds an optimizer
	 *
	 * @param type The optimizer type, either a shorthand name or the {@link Optimizer} class name.
	 * @param returnClass The generated value java type
	 * @param incrementSize The increment size.
	 * @param explicitInitialValue The user supplied initial-value (-1 indicates the user did not specify).
	 *
	 * @return The built optimizer
	 */
	public static Optimizer buildOptimizer(OptimizerDescriptor type, Class<?> returnClass, int incrementSize, long explicitInitialValue) {
		final Optimizer optimizer = buildOptimizer( type, returnClass, incrementSize );
		if ( optimizer instanceof InitialValueAwareOptimizer ) {
			( (InitialValueAwareOptimizer) optimizer ).injectInitialValue( explicitInitialValue );
		}
		return optimizer;
	}

	/**
	 * Determine the optimizer to use when there was not one explicitly specified.
	 */
	public static String determineImplicitOptimizerName(int incrementSize, Properties configSettings) {
		if ( incrementSize <= 1 ) {
			return StandardOptimizerDescriptor.NONE.getExternalName();
		}
		else {
			// see if the user defined a preferred pooled optimizer...
			final String preferredPooledOptimizerStrategy =
					configSettings.getProperty( AvailableSettings.PREFERRED_POOLED_OPTIMIZER );
			if ( isNotEmpty( preferredPooledOptimizerStrategy ) ) {
				return preferredPooledOptimizerStrategy;
			}
			else {
				// otherwise fallback to the fallback strategy
				return StandardOptimizerDescriptor.POOLED.getExternalName();
			}
		}
	}

	private OptimizerFactory() {
	}
}
