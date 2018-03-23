# CICD Pipeline for API Connect via Jenkins

# What is API Connect?
IBM® API Connect for IBM Cloud is an integrated API management offering, where all of the steps in the API lifecycle, and the actions that surround it, are performed within the offering. The steps of the API lifecycle include creating, running, managing, and securing APIs, as depicted in the following diagram.

![Alt text](https://github.com/abhinabsarkar/apiconnect-pipeline-jenkins/blob/master/images/apic_capabilities.png)

More can be read about API Connect platform [here](https://www.ibm.com/support/knowledgecenter/en/SSFS6T/com.ibm.apic.overview.doc/api_management_overview.html)

# Jenkins and API Connect
Automating the deployment and management of APIs developed on API Connect can be done via Jenkins. The figure below shows the promotion of API using Jenkins Distributed Build Architecture. More can be read about the Jenkins Distributed Architecture [here](https://go.cloudbees.com/docs/cloudbees-documentation/cookbook/book.html#_distributed_builds_architecture).

![Alt text](https://github.com/abhinabsarkar/apiconnect-pipeline-jenkins/blob/master/images/High%20level%20architecture.png)

The steps are described below:
1.	Developer will develop the API locally using APIC toolkit and test it. 
2.	The yaml files are then checked-in to source control say git. 
3.	Code commits will trigger a job in Jenkins which will use the APIC toolkit to publish the product to catalog on APIC Development environment. Note that the Jenkins Master Server delegates the job to Jenkins Slave Agent Server.
4.	Since there is no plugin available for APIC, the APIC toolkit has to be installed on the build server (Jenkins Slave Agent server). The job will run on the build server. Jenkins pipeline will then execute the functional testing.
5.	If the testing is successful, the API product will be promoted to the Test environment. The values for each environment can be set by in the API through the use of API properties. https://www.ibm.com/support/knowledgecenter/en/SSFS6T/com.ibm.apic.toolkit.doc/task_set_config_properties.html
6.	After the API is tested on the Test environment, it is promoted to the Staging environment. This promotion will be gated. The gating can be done by any change management tool like Service-Now.
7.	The same is applicable for promotion to the Production environment.

# Jenkins Pipeline
Jenkins Pipeline is a suite of plugins which supports implementing and integrating continuous delivery pipelines into Jenkins. Pipeline provides an extensible set of tools for modeling simple-to-complex delivery pipelines "as code". Refer https://jenkins.io/doc/book/pipeline/getting-started/ for the basics.

# How to create the Jenkins Pipeline
1.	On the Jenkins Dashboard, Click the New Item menu.
2.	Enter an item name, select pipeline project and click ok.
3.	In the pipeline definition, select Pipeline script from SCM from the dropdown. Select the source code manager and provide the repository url and the credentials. Leave the script path as Jenkinsfile.

![Alt text](https://github.com/abhinabsarkar/apiconnect-pipeline-jenkins/blob/master/images/Jenkins%20pipeline.PNG)

# How to create the Jenkinsfile
The Jenkinsfile which is written in groovy script, will comprise of a number of user defined methods for triggering the commands via APIC toolkit. Summary of the core commands in the IBM API Connect toolkit can be found here. https://www.ibm.com/support/knowledgecenter/en/SSFS6T/com.ibm.apic.toolkit.doc/rapim_cli_command_summary.html
Remember that APIC toolkit is installed in Build server which is nothing but Jenkins Slave. The Jenkins Master will act as a portal whereas the Jenkins Agent will act as the worker. (Refer the 1st diagram and explanation in step 4 under that)

# Code
Sample code promoting an API product from dev, test, staging and finally to production is added here. The code doesn't cover automated functional testing but it can be added in the pipeline. Similarly, it also doesn't include the change management validation but the architecture supports it and can be easily integrated with tools like service-now.
