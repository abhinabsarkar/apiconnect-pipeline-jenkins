/*
This Jenkinsfile facilitates the promotion of APIC artifacts to the following environments:
Dev, Test and Production.
In the Dev and Test environments, the API artifacts are published to Catalog.
In Production environments, the API artifacts are published to a Space in the Catalog.
Publishing to Production can be integrated with Change Control if it is exposed as an API.
In Production, Catalog's development mode is switched off. The pipeline is capable of deploying 
different versions of an API.
*/

//Import libraries
import hudson.util.Secret
import java.text.SimpleDateFormat
import org.apache.commons.lang3.time.DateUtils
import hudson.AbortException

// TFS Git url of the repo/folder where this Jenkinsfile is placed
jenkinsfileURL = "" //Eg: https://tfs.mydomain.com/myrepo/_git/myapi
jenkinsfileBranch = "master"

//Credential objects defined in Jenkins
//Credential objects name in Jenkins for Active Directory Service Account
tfsCredentials = ""
//Credential object name in Jenkins for LDAP Service Account
//This credential will be used for Dev & Test environments
apicTestCredentials = ""
//Credential object name in Jenkins for LDAP Service Account
//This credential will be used for Production environments
apicProductionCredentials = ""

//Approvers and Manager's ID
//AD ID of the developer who will initiate the job in Jenkins
def leadDeveloperID = ""
//AD ID of the manager who will approve the Change Control from App Team
def appdevManagerID = ""
//Email ID of the manager who will approve the Change Control from App Team
def appdevManagerEmail = ""

//Product yaml file
product = "" //Eg: "my-product_1.0.0.yaml"
//Name of the API Product in yaml file
productName = "" //Eg: "my-product"
//Visibility parameters
visibility = "" //Eg: "public", "authenticated", "<dev-org-name>", "<tag-name>"
subscribability = "" //Eg: "authenticated", "<dev-org-name>", "<tag-name>"
version = "" //Eg: 1.0.0

//APIC Environment variables
//Dev Environment
//APIM server in Dev environment
devServer = ""
//API Provider Catalog Name. (It is not display name)
devCatalog = ""
//API Provider Org Name. (It is not display name)
devOrg = ""
//Test Environment
//APIM server in Test environment
testServer = ""
//API Provider Catalog Name. (It is not display name)
testCatalog = ""
//API Provider Org Name. (It is not display name)
testOrg = ""
//APIM Production Environment
//APIM Load Balancer in Production environment
productionServerInternal = ""
//API Provider Space Name. (It is not display name)
productionSpaceInternal = ""
//API Provider Catalog Name. (It is not display name)
productionCatalogInternal = ""   //In Internal Facing Production, the artifacts are published on this Catalog
//API Provider Org Name. (It is not display name)
productionOrgInternal = ""       //In Production, the artifacts are published on this Org

node('jenkins-agent-apic') {    //This is the label which is defined in Jenkins Master and runs in slave node

    try{
        echo "Workspace: ${env.WORKSPACE}"

        //Checkout the code 
        GitCheckout(env.WORKSPACE, jenkinsfileURL, jenkinsfileBranch, tfsCredentials)

        //Provide the option to choose the deployment environment
        def environmentChoices = ['Dev', 'Test', 'Production'].join('\n')
        def environment = null
        environment = input(message: "Choose the environment for deployment ?",
                    parameters: [choice(choices: environmentChoices, name: 'Environment')])        

        //This method will accept the apic license.
        //Not needed in the container environment as it is already done while building the image.         
        //Apic_Initiate()    

        //Publish to dev server
        if(environment == 'Dev'){
            Deploy(devServer, apicTestCredentials, product, devCatalog, devOrg)
        }

        //Publish to test server
        if(environment == 'Test'){
            Deploy(testServer, apicTestCredentials, product, testCatalog, testOrg)   
        } 
     
        //Validate change control & deploy to production server
        if(environment == 'Production'){
            ValidateCCAndDeploy(productionServerInternal, apicProductionCredentials, product, productionCatalogInternal, productionOrgInternal, 
                leadDeveloperID, appdevManagerID, appdevManagerEmail, productionSpaceInternal, "Production", productName)       
        }       

    } catch(exe)
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
 *  (From the credentials plugin in Jenkins)
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

def Apic_Initiate() {
    //accept the apic license and disable collection of usage analytics
    sh "echo n| apic --accept-license"    
    
}

//Publish API artifacts to APIM server.  
//This applies only for Dev & Test environment as there is no Change Control included.
def Deploy(String server, String creds, String product, String catalog, String org, String space = "", 
    String versioning = "false", String productName = "", String currentVersion = "", String newVersion = "", String planMapping = "") {
        //Login to APIM Server
        try {
            echo "Attempting Login to ${server}"                     
            Login(server, creds)                      
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
            def newProductFileName = "${productName}_${newVersion}.yaml" //assumption is file name will have the following pattern            
            def status = Stage(newProductFileName, catalog, org, server, space)
            //If the status code is non zero i.e. staging failed, abort the pipeline.
            if(status != 0) 
            {
                currentBuild.result = "FAILED"
                error("[FAILURE] Failed to stage the product ${productName} ${newVersion}")
            } 

            //Replace the existing product version with the new version 
            status = Replace(productName, currentVersion, newVersion, planMapping, catalog, org, server, space)
            if(status != 0) 
            {
                currentBuild.result = "FAILED"
                error("[FAILURE] Failed to replace the product ${productName} with the new version ${newVersion} from the current version ${currentVersion}")
            } 
            else 
            {
                //Set the visibility for the new version
                status = SetVisibility(product, productName, catalog, org, server, space, newVersion, visibility, subscribability) 
                if(status != 0) 
                {
                    currentBuild.result = "FAILED"
                    error("[FAILURE] Failed to set the visibility of the product ${productName} with the new version ${newVersion}")
                }                
            }             
        }

        //Logout from APIM server
        logoutFailed = false
        try {
            Logout(server)
            echo "Logged out of ${server}"
        } catch (exe) {
            echo "Failed to Log out with Status Code: ${exe}, check Environment manually."
            logoutFailed = trued
            throw exe
        }    
}

//Login to APIM  server
def Login(String server, String creds){
    def usernameVariableLocal, passwordVariableLocal
    withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: "${creds}",
    usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD']]) {                        
        usernameVariableLocal = env.USERNAME
        passwordVariableLocal = env.PASSWORD
        
        //Ensure the windows batch script is called from withCredentials step else the 
        //credentials will not be masked in the log output        
        sh "apic login -s ${server} -u ${usernameVariableLocal} -p ${passwordVariableLocal}"        
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
        def status = sh script: "apic publish ${product} --catalog ${catalog} --organization ${org} --server ${server}", 
            returnStatus: true  
        if (status == 0) {
            status = SetVisibility(product, productName, catalog, org, server, space, version, visibility, subscribability)                 
            return status             
        }
    }
    else {
        def status = sh script: "apic publish --scope space ${product} --space ${space} --catalog ${catalog} --organization ${org} --server ${server}", 
            returnStatus: true  
        if (status == 0) {
            status = SetVisibility(product, productName, catalog, org, server, space, version, visibility, subscribability)                 
            return status             
        }                    
    }    
} 

//Set Visibility of the API Product
def SetVisibility(String product, String productName, String catalog, String org, String server, String space = "", String version, String visibility, String subscribability){
    echo "Setting the visibility of the API product ${product}"
    if (!space.trim()) {
        def status = sh script: "apic products:set ${productName}:${version} --visibility ${visibility} --subscribability ${subscribability} --catalog ${catalog} --organization ${org} --server ${server}", 
            returnStatus: true  
        return status  
    }
    else {
        def status = sh script: "apic products:set --scope space ${productName}:${version} --visibility ${visibility} --subscribability ${subscribability} --space ${space} --catalog ${catalog} --organization ${org} --server ${server}", 
            returnStatus: true  
        return status          
    }    
}

//Stage the artifacts to APIM server
def Stage(String product, String catalog, String org, String server, String space = "") {
    echo "Staging product ${product}"
    if (!space.trim()) {
        def status = sh script: "apic publish --stage ${product} --catalog ${catalog} --organization ${org} --server ${server}", 
            returnStatus: true  
        return status  
    }
    else {
        def status = sh script: "apic publish --stage --scope space ${product} --space ${space} --catalog ${catalog} --organization ${org} --server ${server}", 
            returnStatus: true  
        return status          
    }     
}

//Replace the existing product with a new version of the product
def Replace(String productName, String currentVersion, String newVersion, String planMap, String catalog, String org, String server, String space = ""){
    echo "Replacing the existing product ${productName}: ${currentVersion} with the new version of the product ${newVersion}"
    def status = sh script: "apic products:replace --scope space ${productName}:${currentVersion} ${productName}:${newVersion} --plans ${planMap} --space ${space} --catalog ${catalog} --organization ${org} --server ${server}",
        returnStatus:true   
    return status
}


/*
*Validate change control
*Deploy to production
*/
def ValidateCCAndDeploy(String server, String creds, String product, String catalog, String org,
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
                Deploy(server, creds, product, catalog, org, space)
            }
            else { 
                print "Please enter current version number of the product."               
                //Get the new version number
                def currentVersion = input(
                        id: 'currentVersion', message: 'Please enter current version number of the product.', parameters: [
                        [$class: 'TextParameterDefinition', defaultValue: '0.0.0', description: 'Current Version Number', name: 'currentVersionNumber']
                ]) 
                               
                print "Please enter new version number of the product."                
                //Get the new version number
                def newVersion = input(
                        id: 'newVersion', message: 'Please enter new version number of the product.', parameters: [
                        [$class: 'TextParameterDefinition', defaultValue: '0.0.0', description: 'New Version Number', name: 'newNersionNumber']
                ]) 

                print "Please enter plan mapping."
                //Get the plan mapping
                def planMap = input(
                        id: 'planMap', message: 'Please enter plan mapping.', parameters: [
                        [$class: 'TextParameterDefinition', defaultValue: 'default:default', description: 'Plan Mapping', name: 'planMapping']
                ])

                //Replace the existing product with the new version & corresponding plan                               
                Deploy(server, creds, product, catalog, org, space, "true", productName, currentVersion, newVersion, planMap)                
            }
                                     
            changeOption = false                     
        }
        else{   
                //Change should not be promoted to Production         
                throw new AbortException("FAILED")
        }
    }    
}