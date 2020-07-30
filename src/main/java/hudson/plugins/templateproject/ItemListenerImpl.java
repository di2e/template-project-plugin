package hudson.plugins.templateproject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import hudson.matrix.MatrixProject;
import hudson.model.Hudson;
import hudson.model.Project;
import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.listeners.ItemListener;
import hudson.tasks.BuildWrapper;
import hudson.tasks.Builder;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This ItemListener implementation will force job using
 * template for publisher to regenerate the transient actions list.
 *
 * To do so we use the {@link UpdateTransientProperty} property which does not do
 * anything. This is a bit a hack that relies on the behavior, but
 * there does not seem to be any better way to force updating projects transients actions.
 *
 * @author william.bernardet@gmail.com
 *
 */
@Extension
public class ItemListenerImpl extends ItemListener {
	private static final Logger LOGGER = Logger.getLogger(ItemListenerImpl.class.getName());

	private static final int NUM_PROJECTS_PARALLEL_LOAD = 100;

	/**
	 * Let's force the projects using either the ProxyPublisher or the ProxyBuilder
	 * to update their transient actions.
	 */
	@Override
	public void onLoaded() {
		List<AbstractProject> projects = Hudson.getInstance().getAllItems(AbstractProject.class);

		if (projects.size() > NUM_PROJECTS_PARALLEL_LOAD) {
			ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
			Collection<Future> futures = new ArrayList<Future>();
			for ( final AbstractProject<?,?> project : projects) {
				futures.add( executor.submit( new Runnable() {
					@Override
					public void run() {
						loadProject( project );
					}
				} ) );
			}
			for (Future future : futures) {
				try {
					future.get();
				} catch ( InterruptedException e ) {
					LOGGER.log( Level.WARNING, "Project loading did not complete", e );
				} catch ( ExecutionException e ) {
					LOGGER.log( Level.WARNING, "Project loading did not complete", e );
				}
			}
			executor.shutdown();
		} else {
			for ( final AbstractProject<?,?> project : projects) {
				loadProject( project );
			}
		}
	}

	private void loadProject(AbstractProject<?,?> project) {
		if (project.getPublishersList().get(ProxyPublisher.class) != null ||
				hasBuilder(project, ProxyBuilder.class)
				|| hasBuildWrappers(project, ProxyBuildEnvironment.class)) {
			try {
				LOGGER.info("Loading project: " + project.getDisplayNameOrNull());
				project.addProperty(new UpdateTransientProperty());
				project.removeProperty(UpdateTransientProperty.class);
			} catch (IOException e) {
				LOGGER.severe(e.getMessage());
			}
		}
	}

	private List<Builder> getBuilders(AbstractProject<?, ?> project) {
		if (project instanceof Project) {
			return ((Project)project).getBuilders();
		} else if (project instanceof MatrixProject) {
			return ((MatrixProject)project).getBuilders();
		} else {
			return Collections.emptyList();
		}
	}

	 private List<BuildWrapper> getBuildWrappers(AbstractProject<?, ?> project) {
		if (project instanceof Project) {
			return ((Project)project).getBuildWrappersList();
		} else if (project instanceof MatrixProject) {
			return ((MatrixProject)project).getBuildWrappersList();
		} else {
			return Collections.emptyList();
		}
	}

	public <T> boolean hasBuilder(AbstractProject<?, ?> project, Class<T> type) {
		for (Builder b : getBuilders(project)) {
			if (type.isInstance(b)) {
				return true;
			}
		}
		return false;
	}

	 public <T> boolean hasBuildWrappers(AbstractProject<?, ?> project, Class<T> type) {
		for (BuildWrapper b : getBuildWrappers(project)) {
			if (type.isInstance(b)) {
				return true;
			}
		}
		return false;
	}
}
