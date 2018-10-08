def UPSTREAM_PROJECTS_LIST = [ "Mule-runtime/metadata-model-api/master",
                               "Mule-runtime/mule-runtime-event-model/master",
                               "Mule-runtime/mule-api/MULE-15785",
                               "Mule-runtime/mule-extensions-api/master" ]

Map pipelineParams = [ "UPSTREAM_PROJECTS" : UPSTREAM_PROJECTS_LIST.join(',') ]

runtimeProjectsBuild(pipelineParams)
