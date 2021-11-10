param (
  $PathToOwners, # should be in relative form from root of repo. EG: sdk/servicebus
  $RootDirectory, # ideally $(Build.SourcesDirectory)
  $ToolVersion = "", # Placeholder. Will update in next PR
  $ToolPath = "$env:AGENT_TOOLSDIRECTORY", # The place to check the tool existence. Put $(Agent.ToolsDirectory) as default
  $DevOpsFeed = "https://pkgs.dev.azure.com/azure-sdk/public/_packaging/azure-sdk-for-net/nuget/v3/index.json", # DevOp tool feeds.
  $WorkingDirectory = "$env:SYSTEM_DEFAULTWORKINGDIRECTORY", # The place we fetch CODEOWNERS file
  $VsoVariable = "" # target devops output variable
)

$EngOutputIndicator = ".*EngToolOutputProperty: (.*)"

# Check if the retrieve-codeowners tool exsit or not.
if (!(Test-Path "$ToolPath/retrieve-code-owners.exe")) {
  Write-Host "Installing retrieve-codeowners tool first..."
  dotnet tool install --tool-path $ToolPath --add-source $DevOpsFeed --version $ToolVersion "Azure.Sdk.Tools.RetrieveCodeOwners"
}

# Get the json property from tool command.
$codeOwnerConsoleOutput = & "$ToolPath/retrieve-code-owners" --target-directory "$PathToOwners" --root-directory "$WorkingDirectory" 

# Failed at the command of fetching code owners.
if ($LASTEXITCODE -ne 0) {
  Write-Host $codeOwnerConsoleOutput
  return
}

# Parsing the json property from console output.
$codeOwnerConsoleOutput -match $EngOutputIndicator
$codeOwnersMatches = $Matches[1] 
$codeOwnerJson = $codeOwnersMatches | ConvertFrom-Json
if (!$codeOwnersJson) {
  Write-Host "There is something wrong with parsing logic. Check output: $codeOwnerConsoleOutput"
  return
}

return $codeOwnerJson.Owners -join ","
