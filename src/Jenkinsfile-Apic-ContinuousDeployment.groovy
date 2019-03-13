//For windows server, curl for windows is needed to be installed and the script will be run using bat command

// Pull in default Jenkinsfile
jenkinsfileURL = ""
jenkinsfileBranch = "master"

product = "sample-product_1.0.0.yaml"
//Name of credential object in Jenkins
creds = ""

//APIC Environment variables
devServer = ""
devCatalog = ""
devOrg = ""

testServer = ""
testCatalog = ""
testOrg = ""

serverStg = ""
spaceStg = ""
catalogStg = ""
orgStg = ""

prodServer = ""
prodCatalog = ""
prodOrg = ""
prodSpace = ""

node('jenkins-slave-label') {    

    try{
        echo "Workspace: ${env.WORKSPACE}"

        gitCheckout(env.WORKSPACE, jenkinsfileURL, jenkinsfileBranch, creds)  

        apic_initiate()    

        //Publish to dev server
        deploy(devServer, creds, product, devCatalog, devOrg)

        //Publish to test server
        deploy(testServer, creds, product, testCatalog, testOrg)

        //Publish to staging server
        deploy(serverStg, creds, product, catalogStg, orgStg)

        //Publish to production server
        deploy(prodServer, creds, product, prodCatalog, prodOrg, prodSpace)                

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
 *  (From the credentials plugin in Cloudbees)
 */
def gitCheckout(String workspace, String url, String branch, String credentialsId) {
    withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: "${credentialsId}",
                      passwordVariable: 'pass', usernameVariable: 'user']]) {

        // Checkout the code and navigate to the target directory
        int slashIdx = url.indexOf("://")
        String urlWithCreds = url.substring(0, slashIdx + 3) +
                "\"${user}:${pass}\"@" + url.substring(slashIdx + 3);        
        
        //Delete the content of the workspace and clone the source code into current directory
        sh """
            rm -fr ${workspace}/*
            git clone -b ${branch} ${urlWithCreds} .
            cd ${workspace}
            echo `pwd && ls -l     
        """
    }
}

def apic_initiate() {
    //accept the apic license and disable collection of usage analytics
    sh "echo n| apic --accept-license"    
}

def deploy(String server, String creds, String product, String catalog, String org, String space = "") {
        //Login to Dev Server
        try {
            echo "Attempting Login to ${server}"                     
            login(server, creds)                      
        } catch(exe){
            echo "Failed to Login to ${server} with Status Code: ${exe}"
            throw exe                       
        }  

        //Publish to dev server
        //If the status code is non zero i.e. publish failed, abort the pipeline.
        //def status = apic.publish(product, catalog, org, server, space)
        def status = publish(product, catalog, org, server, space)
        if(status != 0) 
        {
            currentBuild.result = "FAILED"
            error("[FAILURE] Failed to publish ${product}")
        }  

        //Logout for dev server
        logoutFailed = false
        try {
            //apic.logout(server)
            logout(server)
            echo "Logged out of ${server}"
        } catch (exe) {
            echo "Failed to Log out with Status Code: ${exe}, check Environment manually."
            logoutFailed = true
            throw exe
        }    
}

def login(String server, String creds){
    def usernameVariableLocal, passwordVariableLocal
    withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: "${creds}",
    usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD']]) {                        
        usernameVariableLocal = env.USERNAME
        passwordVariableLocal = env.PASSWORD
        
        //Ensure the windows batch script is called from withCredentials step else the 
        //credentials will not be masked in the log output        
        sh "apic login -s ${server} -u ${usernameVariableLocal} -p ${passwordVariableLocal}"        
    } 
    echo "Successfully Logged In:  ${usernameVariableLocal}@${server}"
}

def logout(String server){
    sh "apic logout -s ${server}"
}

//Publish to Catalog or Space
def publish(String product, String catalog, String org, String server, String space = ""){
    echo "Publishing product ${product}"
    if (!space.trim()) {
        def status = sh script: "apic publish ${product} --catalog ${catalog} --organization ${org} --server ${server}", 
            returnStatus: true  
        return status  
    }
    else {
        def status = sh script: "apic publish --scope space ${product} --space ${space} --catalog ${catalog} --organization ${org} --server ${server}", 
            returnStatus: true  
        return status          
    }    
} 

