# vim: ts=4:sw=4:et:ft=hcl

resource "random_string" "rabbit_password" {
  length = 17
  special = false
}

resource "random_string" "aries_http_search_user_password" {
  length = 17
  special = false
}

resource "random_string" "aries_http_command_user_password" {
  length = 17
  special = false
}

resource "random_string" "argo_http_auth_user_password" {
  length = 17
  special = false
}

resource "random_string" "cortex_http_search_user_password" {
  length = 17
  special = false
}

resource "random_string" "online_prediction_password" {
  length = 17
  special = false
}

resource "random_string" "orion_http_search_user_password" {
  length = 17
  special = false
}

resource "random_string" "pegasus_http_auth_user_password" {
  length = 17
  special = false
}

resource "random_string" "mongodb_app_password" {
  length = 17
  special = false
}

resource "random_string" "mongodb_rootadmin_password" {
  length = 17
  special = false
}

resource "random_string" "mongodb_useradmin_password" {
  length = 17
  special = false
}

resource "random_string" "mongodb_clusteradmin_password" {
  length = 17
  special = false
}

resource "random_string" "mongodb_clustermonitor_password" {
  length = 17
  special = false
}

resource "random_string" "mongodb_backup_password" {
  length = 17
  special = false
}

resource "random_string" "gemini_http_auth_password" {
  length = 17
  special = false
}

resource "random_string" "baile_http_auth_user_password" {
  length = 17
  special = false
}


