# vim: ts=4:sw=4:et:ft=hcl

variable "elb_name" {}
variable "elb_security_group" {}
variable "subnets" {
  type = "list"
}
variable "health_check_target" {
  description = "The URL the ELB should use for health checks"
  // This is primarily used with http or https backend protocols
  // The format is like `HTTPS:443/health`
}
variable "healthy_threshold" {
  default = "2"
}
variable "unhealthy_threshold" {
  default = "2"
}
variable "timeout" {
  default = "3"
}
variable "interval" {
  default = "10"
}
variable "cross_zone_load_balancing" {
  default = "true"
}
variable "idle_timeout" {
  default = "300"
}
variable "connection_draining" {
  default = "true"
}
variable "connection_draining_timeout" {
  default = "300"
}

variable "tags" {
    description = "Resource tags"
    type        = "map"
    default     = {}
}

# Route53
variable "dns_zone_id" {
  description = "Route53 zone id"
  default = ""
}

variable "dns_records" {
  description = "List of DNS records"
  type        = "list"
  default     = []
}

variable "dns_eval_target_health" {
  description = "Toggle target health evaluation"
  default = "false"
}

variable "dns_failover_type" {
  description = "Failover routing policy. Values: ''|PRIMARY|SECONDARY"
  default = ""
}
