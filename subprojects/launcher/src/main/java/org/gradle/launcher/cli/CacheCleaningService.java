package org.gradle.launcher.cli;

import java.io.File;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * Removes old files in the Gradle cache.
 */
public class CacheCleaningService {
	/**
	 * Removes files in the current user's Gradle cache that have a modified
	 * date prior to the specified 'deleteBefore' date.
	 * @param deleteBefore Date before which files should be deleted.
	 */
	public void cleanCache(LocalDateTime deleteBefore) {
		cleanCache(deleteBefore, new File(System.getProperty("user.home")
			+ "/.gradle/caches/modules-2"));
	}

	private boolean cleanCache(LocalDateTime deleteBefore, File dir) {
		assert dir.isDirectory();

		boolean allChildrenDeleted = true;
		Set<File> regularFiles = new HashSet<File>();
		for (File child : dir.listFiles()) {
			if (child.isDirectory()) {
				// clean out child directory, if possible
				allChildrenDeleted = cleanCache(deleteBefore, child) && allChildrenDeleted;
			} else {
				// add child file to regular file list
				regularFiles.add(child);
			}
		}

		// remove all regular files if sibling directories have been removed
		allChildrenDeleted = canDeleteAllFiles(deleteBefore, regularFiles) && allChildrenDeleted;
		if (allChildrenDeleted) {
			for (File file : regularFiles) {
				allChildrenDeleted = allChildrenDeleted && file.delete();
			}
		}

		// delete this directory if it's empty
		if (allChildrenDeleted && lastModified(dir).isBefore(deleteBefore)) {
			return dir.delete();
		} else {
			return false;
		}
	}

	/**
	 * Determine if the entire set of files falls before the deleteBefore time.
	 */
	private boolean canDeleteAllFiles(LocalDateTime deleteBefore, Set<File> files) {
		for (File file : files) {
			if (!lastModified(file).isBefore(deleteBefore)) {
				return false;
			}
		}
		return true;
	}

	private LocalDateTime lastModified(File file) {
		return LocalDateTime.ofInstant(new Date(file.lastModified()).toInstant(),
				ZoneId.systemDefault());
	}
}
