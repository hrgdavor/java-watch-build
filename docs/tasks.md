
# build V2

## Output definition
	- task reference
	- description
	- output

Output is registered globally so conflicts can be noticed. Listeners then can be notified
for each output to have a registry of definitions

Output generation is logged. 
 - listener gets a notification that output is regenerated, 
 - listener reads other info about source and description from the registry.



### Simple task
 - single output
 - single source
 
 Multiple simple outputs can be sourced from same file (en.yml -> en.properties, en.js, en.json)

### Complex task
 - single output
 - multiple source files
 

## Tasks

### Copy Task
  - simple task
  - input file
  - copy to file