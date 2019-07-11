/*
This Jenkinsfile facilitates the promotion of APIC artifacts to the following environments:
    Dev, Test, Staging and Production.
In the Dev and Test environments, the API artifacts are published to Catalog.
In Production environment, the API artifacts are published to a Space in the Enterprise Catalog.
Publishing to Production requires Change Control.
*/

//Import libraries
import hudson.util.Secret
import java.text.SimpleDateFormat
import org.apache.commons.lang3.time.DateUtils
import hudson.AbortException
import java.io.File
//Import libraries for RejectedAccessException
import jenkins.model.*
import hudson.model.*
import org.jenkinsci.plugins.scriptsecurity.sandbox.RejectedAccessException

// Git url of the repo/folder where this Jenkinsfile is placed
jenkinsfileURL = "" //Eg: https://github.com/abhinabsarkar/apiconnect-pipeline-jenkins/blob/master/src/Jenkinsfile
jenkinsfileBranch = "master"

//Credential objects defined in Jenkins
gitCredentials = ""

//Product yaml file
def product = "" //Eg: "sample-product_1.0.0.yaml"
//Name of the API Product in yaml file
def productName = "" //Eg: "sample-product"

node('jenkins-agent-apic2018-linux') { //This is the label which is defined in Jenkins Master and runs in worker node

    try{
        echo "Workspace: ${env.WORKSPACE}"        

        //Checkout the code 
        GitCheckout(env.WORKSPACE, jenkinsfileURL, jenkinsfileBranch, gitCredentials)

        //Load the apic & jenkins variables from the property files
        def apic = readProperties file: 'environment.properties'
        def jenkins = readProperties file: 'jenkins.properties'
 
        //Provide the option to choose the deployment environment
        def environmentChoices = ['Dev', 'Test', 'InternalProduction'].join('\n')
        def environment = null
        environment = input(message: "Choose the environment for deployment ?",
                    parameters: [choice(choices: environmentChoices, name: 'Environment')])        

        //Publish to dev server
        if(environment == 'Dev'){
            Deploy(apic["devServer"], jenkins["apicCredentials"], product, apic["devCatalog"], 
                apic["devOrg"], apic["devRealm"])
        }

        //Publish to test server
        if(environment == 'Test'){
            Deploy(apic["testServer"], jenkins["apicCredentials"], product, apic["testCatalog"], 
                apic["testOrg"], apic["testRealm"])   
        } 
      
        //Validate change control & deploy to production server
        if(environment == 'Production'){
            ValidateCCAndDeploy(apic["productionServer"], jenkins["apicCredentials"], product, 
                apic["productionCatalog"], apic["productionOrg"], apic["productionRealm"], 
                jenkins["leadDeveloperID"], jenkins["appdevManagerID"], jenkins["appdevManagerEmail"], 
                apic["productionSpace"], "Production", productName)       
        }       
        //Method Requiring Script Approval Here
    } catch(RejectedAccessException exe)
    {
        throw exe
    } catch (exe)
    {
        echo "${exe}"
        error("[FAILURE] Failed to publish ${product}")
    }
}    

/**
 * Clones and checks out the given Git repository branch and commit
 * @param  String workspace     Path of the working directory to use
 * @param  String url           URL of the Git repository
 * @param  String credentialsId Id of the Git credentials to use
 *  (From the credentials plugin in Cloudbees)
 */
def GitCheckout(String workspace, String url, String branch, String credentialsId) {
    withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: "${credentialsId}",
                      passwordVariable: 'pass', usernameVariable: 'user']]) {

        // Checkout the code and navigate to the target directory
        int slashIdx = url.indexOf("://")
        String urlWithCreds = url.substring(0, slashIdx + 3) +
                "\"${user}:${pass}\"@" + url.substring(slashIdx + 3);        
        
        //Get the work folder name
        folderName = sh (
            script:"""
            basename ${workspace}
            """,
            returnStdout: true
        )
        
        //Delete the folder in the workspace and clone the source code into directory.
        //The folder has to be deleted because the contents has to be empty for git to clone in.
        sh """
            cd ${workspace}
            cd ..
            rm -fr ${folderName}
            mkdir ${folderName}
            cd ${folderName}
            git clone -b ${branch} ${urlWithCreds} ${workspace}
        """
    }
}

//Publish API artifacts to APIM server.  
//This applies only for Dev & Test environment as there is no Change Control included.
def Deploy(String server, String creds, String product, String catalog, String org, String realm, String space = "", 
    String versioning = "false", String stagingProduct = "", String stagingProductname = "", String newVersion = "", 
    String productPlanMapFile = "") {
        //Login to APIM Server
        try {
            echo "Attempting Login to ${server}"                     
            Login(server, creds, realm)
            //Method Requiring Script Approval Here
        } catch(RejectedAccessException exe)
        {
            throw exe                                 
        } catch(exe){
            echo "Failed to Login to ${server} with Status Code: ${exe}"
            throw exe                       
        }  

        if (versioning == "false") {
            //Publish to APIM server
            def status = Publish(product, catalog, org, server, space)
            //If the status code is non zero i.e. publish failed, abort the pipeline.
            if(status != 0) 
            {
                currentBuild.result = "FAILED"
                error("[FAILURE] Failed to publish ${product}")
            }  
        }
        else {
            //Stage the new product version            
            def status = Stage(stagingProduct, catalog, org, server, space)
            //If the status code is non zero i.e. staging failed, abort the pipeline.
            if(status != 0) 
            {
                currentBuild.result = "FAILED"
                error("[FAILURE] Failed to stage the product ${stagingProduct} ${newVersion}")
            } 

            //Replace the existing product version with the new version 
            status = Replace(stagingProductname, newVersion, productPlanMapFile, catalog, org, server, space)
            if(status != 0) 
            {
                currentBuild.result = "FAILED"
                error("[FAILURE] Failed to replace the product ${productName} with the new version ${newVersion}")
            }             
        }

        //Logout from APIM server
        logoutFailed = false
        try {
            Logout(server)
            echo "Logged out of ${server}"
            //Method Requiring Script Approval Here
        } catch(RejectedAccessException exe)
        {
            throw exe            
        } catch (exe) {
            echo "Failed to Log out with Status Code: ${exe}, check Environment manually."
            logoutFailed = trued
            throw exe
        }    
}

//Login to APIM  server
def Login(String server, String creds, String realm){
    def usernameVariableLocal, passwordVariableLocal
    withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: "${creds}",
    usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD']]) {                        
        usernameVariableLocal = env.USERNAME
        passwordVariableLocal = env.PASSWORD
        
        //Ensure the windows batch script is called from withCredentials step else the 
        //credentials will not be masked in the log output        
        sh "apic login --server ${server} --username ${usernameVariableLocal} --password ${passwordVariableLocal} --realm ${realm}"        
    } 
    //echo "Successfully Logged In:  ${usernameVariableLocal}@${server}"
}

//Logout from APIM server
def Logout(String server){
    sh "apic logout -s ${server}"
}

//Publish artifacts to APIM server
def Publish(String product, String catalog, String org, String server, String space = ""){
    echo "Publishing product ${product}"
    if (!space.trim()) {
        def status = sh script: "apic products:publish ${product} --catalog ${catalog} --org ${org} --server ${server}", 
            returnStatus: true  
        if (status == 0) {                            
            return status             
        }
    }
    else {
        def status = sh script: "apic products:publish --scope space ${product} --space ${space} --catalog ${catalog} --org ${org} --server ${server}", 
            returnStatus: true  
        if (status == 0) {            
            return status             
        }                    
    }    
} 

//Stage the artifacts to APIM server
def Stage(String product, String catalog, String org, String server, String space = "") {
    echo "Staging product ${product}"
    if (!space.trim()) {
        def status = sh script: "apic products:publish --stage ${product} --catalog ${catalog} --org ${org} --server ${server}", 
            returnStatus: true  
        return status  
    }
    else {
        def status = sh script: "apic products:publish --stage --scope space ${product} --space ${space} --catalog ${catalog} --org ${org} --server ${server}", 
            returnStatus: true  
        return status          
    }     
}

//Replace the existing product with a new version of the product
def Replace(String productName, String newVersion, String productPlanMapFile, String catalog, String org, String server, String space = ""){
    echo "Replacing the existing product ${productName} with the new version of the product ${newVersion}"
    def status = sh script: "apic products:replace --scope space ${productName}:${newVersion} ${productPlanMapFile} --space ${space} --catalog ${catalog} --org ${org} --server ${server}",
        returnStatus:true   
    return status
}

/*
*Validate change control
*Deploy to production
*/
def ValidateCCAndDeploy(String server, String creds, String product, String catalog, String org, String realm,
        String leadDeveloperID, String appdevManagerID, String appdevManagerEmail, String space = "", 
        String environment, String productName) {
    
            //Add your code to integrate with Change Control API, if it is applicable         

            //Promote the code to next envionment
            //Check if the API has to be versioned
            print "For the 1st deployment, Versioning is not required. Select it as No."
            print "For the subsequent deployment, Versioning is required. Select it as Yes."
            print "Is the API already published in ${environment} ?"
            def apiVersioningChoices = ['Yes', 'No'].join('\n')
            def apiVersioning = null
            apiVersioning = input(message: "Is API Versioning required ?",
                    parameters: [choice(choices: apiVersioningChoices, name: 'apiVersioning')])

            if (apiVersioning == 'No') {
                //Publish the product 
                Deploy(server, creds, product, catalog, org, realm, space)
            }
            else {  
                print "Please enter staging product file name. Eg:sample_product_1.1.0.yaml"                
                //Get the satging product file name
                def stagingProduct = input(
                        id: 'stagingProduct', message: 'Please enter staging product file name. Eg:sample_product_1.1.0.yaml', parameters: [
                        [$class: 'TextParameterDefinition', defaultValue: 'sample_product_1.1.0.yaml', description: 'Staging Product Version', name: 'stagingProduct']
                ])

                print "Please staging product name. Eg:sample"                
                //Get the staging product name
                def stagingProductName = input(
                        id: 'stagingProductName', message: 'Please enter staging product name. Eg:sample', parameters: [
                        [$class: 'TextParameterDefinition', defaultValue: 'sample', description: 'Staging Product Name', name: 'stagingProductName']
                ])                

                print "Please enter new version number of the product."                
                //Get the new version number
                def newVersion = input(
                        id: 'newVersion', message: 'Please enter new version number of the product.', parameters: [
                        [$class: 'TextParameterDefinition', defaultValue: '0.0.0', description: 'New Version Number', name: 'newNersionNumber']
                ]) 

                print "Please enter the product plan mapping file name. Eg:product-map-file.txt"
                //Get the plan mapping
                def productPlanMapFile = input(
                        id: 'productPlanMapFile', message: 'Please enter the product plan mapping file name. Eg:product-map-file.txt', parameters: [
                        [$class: 'TextParameterDefinition', defaultValue: 'product-map-file.txt', description: 'Plan Mapping', name: 'planMapping']
                ])

                //Replace the existing product with the new version & corresponding plan                               
                Deploy(server, creds, product, catalog, org, realm, space, "true", stagingProduct, stagingProductName, newVersion, productPlanMapFile)                
            }
                                     
            changeOption = false                     
        }
        else{   
                //Change should not be promoted to Production         
                throw new AbortException("FAILED")
        }
    }    
}