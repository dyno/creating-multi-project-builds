package com.signifyd.gradle

import org.gradle.api.tasks.Exec
import org.gradle.api.Task
import org.gradle.model.Defaults
import org.gradle.model.Model
import org.gradle.model.ModelMap
import org.gradle.model.Mutate
import org.gradle.model.RuleSource

class PythonProjectRules extends RuleSource {
    /**
     * @param pythonProject The type {@link PythonProject} has a {@code @Managed}
     *        annotation, so Gradle can provide an implementation.
     */
    @Model
    void pythonProject(final PythonProject pythonProject) {}

    /**
     * Method to set default values for {@link PythonProject} object
     */
    @Defaults
    void defaultsPythonProject(final PythonProject pythonProject) {
        pythonProject.usePipenv = true
        pythonProject.pipenvExecutable = ["pipenv"]
        pythonProject.pipenvArgs = ["run"]
    }

    /**
     * Gradle will make sure the input argument is created and all properties
     * are set before it is used in this method. So no more {@link afterEvaluate}
     * or convention mappings are needed. Gradle makes sure all input arguments
     * are resolved before they are used.
     */
    @Mutate
    void createPythonProjectTasks(final ModelMap<Task> tasks, final PythonProject pythonProject) {
        def targets = [
            "pipenvInstall": "pipenv --three install",
            "pipenvInstallDev": "pipenv --three install --dev",
            //
            "sdist": "python setup.py sdist",
            "bdist": "python setup.py bdist_wheel",
            "test": "python setup.py test",
            //
            "pkgname": "python setup.py --name",
            "bumpversion": "bumpversion patch",
            // NOTE: For reason why we need twine here rather than call `setup.py upload`,
            // please refer to the link https://github.com/pypa/setuptools/issues/954
            "upload": "twine upload", // placeholder
        ]

        if (!pythonProject.usePipenv) {
            targets = targets.findAll { !it.key.startsWith("pipenv") }
        }

        targets.each {target, cmd ->
            tasks.create(target, Exec) { task ->
                task.commandLine = cmd.split()
            }
        }

        tasks.pkgname {
            standardOutput = new ByteArrayOutputStream()
            ext.output = {
                return standardOutput.toString().trim()
            }
        }

        tasks.upload.dependsOn "clean", "bumpversion", "build", "pkgname"
        tasks.upload.doFirst {
            def packageName = tasks.pkgname.output()
            def altPackageName = packageName.replaceAll("-", "_")
            commandLine = [*commandLine,  "dist/${packageName}*.tar.gz", "dist/${altPackageName}*.whl"]
        }

        tasks.clean {
            delete "build"
            delete "dist"
            delete ".venv"
            delete project.file(".").listFiles({ path, filename -> filename ==~ /.*egg-info/ } as FilenameFilter)
        }

        // NOTE: There is a 'build' target in setup.py which is not what we want.
        // `python setup.py --help-commands`
        tasks.build.dependsOn "sdist", "bdist", "test"
        tasks.build.mustRunAfter "bumpversion"
        tasks.bumpversion.mustRunAfter "clean"

        if (pythonProject.usePipenv) {
            tasks.build.dependsOn += "pipenvInstall"
            tasks.test.mustRunAfter "pipenvInstall"
        }

        tasks.withType(Exec) { task ->
            if (pythonProject.usePipenv) {
                if (task.name.startsWith("pipenv")) {
                    task.commandLine = [*pythonProject.pipenvExecutable, *task.commandLine.drop(1)]
                } else {
                    task.commandLine = [*pythonProject.pipenvExecutable, *pythonProject.pipenvArgs, *task.commandLine]
                }
            }

            def cmdStr = task.commandLine.join(" ")
            task.doFirst {
                logger.lifecycle("Running cmd = `${cmdStr}`")
            }
            task.doLast {
                logger.lifecycle("Finished cmd = `${cmdStr}`")
            }
        }
    }
}
