package upm.bd

import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.types._
import org.apache.spark.sql.functions._
import org.apache.log4j.{Level, Logger}

object App {
  def main(args : Array[String]) {
 
	// Disabling debug option. 
	Logger.getRootLogger().setLevel(Level.WARN)

	val spark = SparkSession
		.builder()
		.appName("Spark Flights Delay")
		.getOrCreate()
   
   	import spark.implicits._

	val project = "/project"
	val archive = "2000"

	// Read csv file with headers from hdfs
	var flightsDF = spark.read.format("com.databricks.spark.csv").option("header", "true").load("hdfs://"+project+"/"+archive+".csv")

	// Print schema
	flightsDF.printSchema

	// Forbidden variables
	val forbiddenVariables = Array("ArrTime", "CarrierDelay", "WeatherDelay", "NASDelay", "SecurityDelay", 
		"LateAircraftDelay", "TaxiIn", "Diverted", "ActualElapsedTime", "AirTime")

	// Creating allowed variables extracting the forbidden variables from the original ones.
	val allowedVariables = flightsDF.columns.filter(x => !forbiddenVariables.contains(x))
	// Convert them into column format.
	val allowedColumns = allowedVariables.map(x => col(x))

	// Selecting the just the allowed columns.
	flightsDF = flightsDF.select(allowedColumns:_*)

	//Drop rows with null values in the target variable
	flightsDF = flightsDF.na.drop(Array("ArrDelay"))

	//Drop 

	// Let us convert these variable into TimeStamp
	//flightsDF = flightsDF.withColumn("DepTime", flightsDF("DepTime").cast(DoubleType))
	flightsDF = flightsDF.withColumn("CRSDepTime", flightsDF("CRSDepTime").cast(DoubleType))
	flightsDF = flightsDF.withColumn("CRSArrtime", flightsDF("CRSArrTime").cast(DoubleType))

	// Given a dataframe and the name of a column (string) which has time in format hhmm, it creates a new column based on the day, month, year, hour and minute. 
	def toTimeStamp(df: org.apache.spark.sql.DataFrame, a: String) : org.apache.spark.sql.DataFrame = {
		return df.withColumn(a, unix_timestamp(concat($"DayOfMonth", lit("/"), $"Month", lit("/"), $"Year", lit(" "), col(a)), "dd/MM/yyyy HHmm"))
	}

	//flightsDF = toTimeStamp(flightsDF, "DepTime")
	flightsDF = toTimeStamp(flightsDF, "CRSDepTime")
	flightsDF = toTimeStamp(flightsDF, "CRSArrTime")

	// Transforming the type of variables.
	flightsDF = flightsDF.withColumn("Year", flightsDF("Year").cast(DoubleType))
	flightsDF = flightsDF.withColumn("Month", flightsDF("Month").cast(DoubleType))
	flightsDF = flightsDF.withColumn("DayOfMonth", flightsDF("DayOfMonth").cast(DoubleType))
	flightsDF = flightsDF.withColumn("DayOfWeek", flightsDF("DayOfWeek").cast(DoubleType))
	flightsDF = flightsDF.withColumn("CRSElapsedTime", flightsDF("CRSElapsedTime").cast(DoubleType))
	flightsDF = flightsDF.withColumn("ArrDelay", flightsDF("ArrDelay").cast(DoubleType))
	flightsDF = flightsDF.withColumn("DepDelay", flightsDF("DepDelay").cast(DoubleType))
	flightsDF = flightsDF.withColumn("Distance", flightsDF("Distance").cast(DoubleType))
	flightsDF = flightsDF.withColumn("TaxiOut", flightsDF("TaxiOut").cast(DoubleType))
	flightsDF = flightsDF.withColumn("Cancelled", flightsDF("Cancelled").cast(DoubleType))
	flightsDF = flightsDF.withColumn("CRSDepTime", flightsDF("CRSDepTime").cast(DoubleType))
	flightsDF = flightsDF.withColumn("CRSArrTime", flightsDF("CRSArrTime").cast(DoubleType))

	// We remove the cancelled flights since they do not contain information about the target variable (ArrDelay).
	flightsDF = flightsDF.filter(col("Cancelled") === false)
	// We remove such column and the CancellationCode
	flightsDF = flightsDF.drop("Cancelled").drop("CancellationCode")

	val toDayOfWeek: Double => String =  _ match {
	case 1 => "Monday"
	case 2 => "Tuesday"
	case 3 => "Wednesday"
	case 4 => "Thursday"
	case 5 => "Friday"
	case 6 => "Saturday"
	case 7 => "Sunday"
	}

	val toDayOfWeekDF = udf(toDayOfWeek)
	//flightsDF = flightsDF.withColumn("DayOfWeek", toDayOfWeekDF($"DayOfWeek"))

	val toMonth: Double => String =  _ match {
	case 1 => "January"
	case 2 => "February"
	case 3 => "March"
	case 4 => "April"
	case 5 => "May"
	case 6 => "June"
	case 7 => "July"
	case 8 => "August"
	case 9 => "September"
	case 10 => "October"
	case 11 => "November"
	case 12 => "December"
	}

	val toMonthDF = udf(toMonth)
	//flightsDF = flightsDF.withColumn("Month", toMonthDF($"Month"))

	// Normalize UNIX time, we take as reference point the earliest year in the database.
	val timeStampReference = unix_timestamp(lit("01/01/1987"), "dd/MM/yy")
	flightsDF = flightsDF.withColumn("CRSDepTime", $"CRSDepTime" - timeStampReference)
	flightsDF = flightsDF.withColumn("CRSArrTime", $"CRSArrTime" - timeStampReference)

	// Eliminate DepTime since DepTime = CRSDepTime + DepDelay by definition
	flightsDF = flightsDF.drop("DepTime")


  }

}
