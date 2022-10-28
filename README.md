RLogpresso
==========

Logpresso SDK for R

# Installation #

Using the 'devtools' package:

	> install.packages("rJava")
	> install.packages("devtools")
	> library("devtools")
	> install_github("logpresso/RLogpresso")
	> library("RLogpresso")

# Usage #
	
	> client <- RLogpresso.create()
	> client$connect("localhost", 8888, "id", "password")
	> data <- client$query("table duration=2h sys_cpu_logs | timechart span=2m avg(kernel+user) as value")
	> summary(data)

# Support # 
 
You can get support from Eediom with the following methods:

- Send email to contact@logpresso.com with describing your problem
- [**Logpresso Query Manual**](https://docs.logpresso.com/ko/query)
