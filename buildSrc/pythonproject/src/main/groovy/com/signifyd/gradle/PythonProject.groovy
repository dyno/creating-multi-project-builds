package com.signifyd.gradle

import org.gradle.model.Managed

@Managed
interface PythonProject {
    Boolean getUsePipenv()
    void setUsePipenv(final Boolean usePipenv)

    List<String> getPipenvExecutable()
    void setPipenvExecutable(final List<String> pipenvExecutable)

    List<String> getPipenvArgs()
    void setPipenvArgs(final List<String> pipenvArgs)
}
