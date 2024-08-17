locals {
  aws_region = "us-east-1"
  json_data  = file("./data.json")
  tf_data    = jsondecode(local.json_data)
}