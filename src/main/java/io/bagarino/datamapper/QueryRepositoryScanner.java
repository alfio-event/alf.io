/**
 * This file is part of bagarino.
 *
 * bagarino is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * bagarino is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with bagarino.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.bagarino.datamapper;

import java.util.Set;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

public class QueryRepositoryScanner implements BeanFactoryPostProcessor {

	private final QueryFactory queryFactory;
	private final String[] packagesToScan;

	public QueryRepositoryScanner(QueryFactory queryFactory, String... packagesToScan) {
		this.queryFactory = queryFactory;
		this.packagesToScan = packagesToScan;
	}

	private static class CustomClasspathScanner extends ClassPathScanningCandidateComponentProvider {

		public CustomClasspathScanner() {
			super(false);
			addIncludeFilter(new AnnotationTypeFilter(QueryRepository.class, false));
		}

		@Override
		protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
			return beanDefinition.getMetadata().isInterface();
		}
	}

	@Override
	public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
		if (packagesToScan != null) {
			CustomClasspathScanner scanner = new CustomClasspathScanner();
			for (String packageToScan : packagesToScan) {
				Set<BeanDefinition> candidates = scanner.findCandidateComponents(packageToScan);
				handleCandidates(candidates, beanFactory);
			}
		}
	}

	private void handleCandidates(Set<BeanDefinition> candidates, final ConfigurableListableBeanFactory beanFactory) {
		try {
			for (BeanDefinition beanDefinition : candidates) {
				final Class<?> c = Class.forName(beanDefinition.getBeanClassName());
				beanFactory.registerSingleton(beanDefinition.getBeanClassName(), queryFactory.from(c));
			}
		} catch (ClassNotFoundException cnf) {
			throw new IllegalStateException("Error while loading class", cnf);
		}
	}
}
