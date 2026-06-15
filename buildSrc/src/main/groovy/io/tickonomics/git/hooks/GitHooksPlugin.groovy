package io.tickonomics.git.hooks

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Copy

/**
 * First-party buildSrc plugin (no third-party dependency) that keeps the live
 * git hooks in sync with the version-controlled sources in {@code scripts/git-hooks}.
 *
 * Registers an {@code installGitHooks} {@link Copy} task that copies the hook
 * sources into {@code .git/hooks} with the executable bit set, and wires it onto
 * {@code build} so a standard {@code ./gradlew build} installs/refreshes the hooks
 * with no manual step. The task is incremental (Gradle skips it when up-to-date),
 * so on a warm build it is a no-op. See ADR-028.
 */
class GitHooksPlugin implements Plugin<Project> {

    static final String TASK_NAME = 'installGitHooks'
    static final String SOURCE_DIR = 'scripts/git-hooks'
    static final String HOOKS_DIR = '.git/hooks'

    @Override
    void apply(Project project) {
        def installTask = project.tasks.register(TASK_NAME, Copy) {
            group = 'build setup'
            description = "Copies ${SOURCE_DIR}/* into ${HOOKS_DIR} (executable)."
            from(project.layout.projectDirectory.dir(SOURCE_DIR))
            into(project.layout.projectDirectory.dir(HOOKS_DIR))
            filePermissions { unix('rwxr-xr-x') }
        }

        project.plugins.withId('base') {
            project.tasks.named('build').configure { dependsOn installTask }
        }
    }
}
