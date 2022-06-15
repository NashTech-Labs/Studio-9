# vim: ts=4:sw=4:et:ft=hcl

terraform {
    required_version = ">= 0.10.7"
    backend "s3" {}
}

# Retrieve S3 data
data "terraform_remote_state" "s3_buckets" {
  backend = "s3"
  config {
    bucket = "${var.tf_bucket}"
    key    = "${var.aws_region}/${var.environment}/s3_buckets/terraform.tfstate"
    region = "${var.aws_region}"
  }
}

#########################################################
# IAM Users
## Users and Policies

resource "aws_iam_user" "app" {
  count = "${local.create_iam_user}"
  name = "${var.tag_owner}-${var.environment}-app"
  path = "/apps/"
}

resource "aws_iam_access_key" "app" {
  count = "${local.create_iam_user}"
  count = "${local.create_iam}"
  user = "${aws_iam_user.app.name}"
}

# IAM S3 policy for app user

resource "aws_iam_user_policy" "app_s3" {
  count = "${local.create_iam_user}"
  name = "${var.tag_owner}-${var.environment}-app-user-policy"
  user = "${aws_iam_user.app.name}"

  policy = <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Action": [
        "s3:*"
      ],
      "Effect": "Allow",
      "Resource": [
        "${data.terraform_remote_state.s3_buckets.apps_s3_bucket_arn}",
        "${data.terraform_remote_state.s3_buckets.apps_s3_bucket_arn}/*"
      ]
    }
  ]
}
EOF
}

#########################################################
# IAM Roles
## Role and Policies

data "template_file" "assume_role_policy" {
  template = "${file("../../terraform/templates/assume_role_policy${local.include_ssm_service}.tpl")}"
}

resource "aws_iam_role" "bastion_role" {
  count = "${local.multi_iam_role}"
  name = "${var.tag_owner}-${var.environment}-bastion_role"
  path = "/"
  assume_role_policy = "${data.template_file.assume_role_policy.rendered}"
}

resource "aws_iam_role_policy_attachment" "bastion_ssm_attach" {
  count      = "${local.include_ssm * local.multi_iam_role}"
  role       = "${aws_iam_role.bastion_role.name}"
  policy_arn = "arn:${var.arn}:iam::aws:policy/service-role/AmazonEC2RoleforSSM"
}

resource "aws_iam_role_policy" "bastion_policy" {
  count = "${local.multi_iam_role}"
  name = "${var.tag_owner}-${var.environment}-bastion_policy"
  role = "${aws_iam_role.bastion_role.id}"
  policy = <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Action": [
        "s3:*"
      ],
      "Effect": "Allow",
      "Resource": [
        "${data.terraform_remote_state.s3_buckets.apps_s3_bucket_arn}",
        "${data.terraform_remote_state.s3_buckets.apps_s3_bucket_arn}/*",
        "${data.terraform_remote_state.s3_buckets.stack_s3_bucket_arn}",
        "${data.terraform_remote_state.s3_buckets.stack_s3_bucket_arn}/*"
      ]
    }
  ]
}
EOF
}

resource "aws_iam_instance_profile" "bastion_instance_profile" {
  count = "${local.multi_iam_role}"
  name = "${var.tag_owner}-${var.environment}-bastion_role"
  role = "${aws_iam_role.bastion_role.name}"
}

resource "aws_iam_role" "nat_instance_role" {
  count = "${local.create_nat * local.multi_iam_role}"
  name = "${var.tag_owner}-${var.environment}-nat_instance_role"
  path = "/"
  assume_role_policy = "${data.template_file.assume_role_policy.rendered}"
}

resource "aws_iam_role_policy_attachment" "nat_instance_ssm_attach" {
  count = "${local.create_nat * local.include_ssm * local.multi_iam_role}"
  role = "${aws_iam_role.nat_instance_role.name}"
  policy_arn = "arn:${var.arn}:iam::aws:policy/service-role/AmazonEC2RoleforSSM"
}

resource "aws_iam_role_policy" "nat_instance_policy" {
  count = "${local.create_nat * local.multi_iam_role}"
  name = "${var.tag_owner}-${var.environment}-nat_instance_policy"
  role = "${aws_iam_role.nat_instance_role.id}"
  policy = <<EOF
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Sid": "NATInstance",
            "Effect": "Allow",
            "Action": [
                "ec2:DescribeNetworkInterfaces",
                "ec2:DescribeTags",
                "ec2:ModifyInstanceAttribute",
                "ec2:CreateRoute",
                "ec2:ReplaceRoute"
            ],
            "Resource": "*"
        }
    ]
}
EOF
}

resource "aws_iam_instance_profile" "nat_instance_profile" {
  count = "${local.create_nat * local.multi_iam_role}"
  name = "${var.tag_owner}-${var.environment}-nat_role"
  role = "${aws_iam_role.nat_instance_role.name}"
}

resource "aws_iam_role" "bootstrap_role" {
  count = "${local.multi_iam_role}"
  name = "${var.tag_owner}-${var.environment}-bootstrap_role"
  path = "/"
  assume_role_policy = "${data.template_file.assume_role_policy.rendered}"
}

resource "aws_iam_role_policy_attachment" "bootstrap_ssm_attach" {
  count      = "${local.include_ssm * local.multi_iam_role}"
  role       = "${aws_iam_role.bootstrap_role.name}"
  policy_arn = "arn:${var.arn}:iam::aws:policy/service-role/AmazonEC2RoleforSSM"
}

resource "aws_iam_role_policy" "bootstrap_policy" {
  count = "${local.multi_iam_role}"
  name = "${var.tag_owner}-${var.environment}-bootstrap_policy"
  role = "${aws_iam_role.bootstrap_role.id}"
  policy = <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Action": [
        "s3:*"
      ],
      "Effect": "Allow",
      "Resource": [
        "${data.terraform_remote_state.s3_buckets.apps_s3_bucket_arn}",
        "${data.terraform_remote_state.s3_buckets.apps_s3_bucket_arn}/*",
        "${data.terraform_remote_state.s3_buckets.stack_s3_bucket_arn}",
        "${data.terraform_remote_state.s3_buckets.stack_s3_bucket_arn}/*"
      ]
    }
  ]
}
EOF
}

resource "aws_iam_instance_profile" "bootstrap_instance_profile" {
  count = "${local.multi_iam_role}"
  name  = "${var.tag_owner}-${var.environment}-bootstrap_role"
  role = "${aws_iam_role.bootstrap_role.name}"
}

resource "aws_iam_role" "master_role" {
  count = "${local.multi_iam_role}"
  name = "${var.tag_owner}-${var.environment}-master_role"
  path = "/"
  assume_role_policy = "${data.template_file.assume_role_policy.rendered}"
}

resource "aws_iam_role_policy_attachment" "master_ssm_attach" {
  count      = "${local.include_ssm * local.multi_iam_role}"
  role       = "${aws_iam_role.master_role.name}"
  policy_arn = "arn:${var.arn}:iam::aws:policy/service-role/AmazonEC2RoleforSSM"
}

resource "aws_iam_role_policy" "master_policy" {
  count = "${local.multi_iam_role}"
  name = "${var.tag_owner}-${var.environment}-master_policy"
  role = "${aws_iam_role.master_role.id}"
  policy = <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Action": [
        "s3:*"
      ],
      "Effect": "Allow",
      "Resource": [
        "${data.terraform_remote_state.s3_buckets.apps_s3_bucket_arn}",
        "${data.terraform_remote_state.s3_buckets.apps_s3_bucket_arn}/*",
        "${data.terraform_remote_state.s3_buckets.stack_s3_bucket_arn}",
        "${data.terraform_remote_state.s3_buckets.stack_s3_bucket_arn}/*"
      ]
    }
  ]
}
EOF
}

resource "aws_iam_instance_profile" "master_instance_profile" {
  count = "${local.multi_iam_role}"
  name  = "${var.tag_owner}-${var.environment}-master_role"
  role = "${aws_iam_role.master_role.name}"
}

resource "aws_iam_role" "slave_role" {
  count = "${local.multi_iam_role}"
  name = "${var.tag_owner}-${var.environment}-slave_role"
  path = "/"
  assume_role_policy = "${data.template_file.assume_role_policy.rendered}"
}

resource "aws_iam_role_policy_attachment" "slave_ssm_attach" {
  count      = "${local.include_ssm * local.multi_iam_role}"
  role       = "${aws_iam_role.slave_role.name}"
  policy_arn = "arn:${var.arn}:iam::aws:policy/service-role/AmazonEC2RoleforSSM"
}

resource "aws_iam_role_policy" "slave_policy" {
  count = "${local.multi_iam_role}"
  name = "${var.tag_owner}-${var.environment}-slave_policy"
  role = "${aws_iam_role.slave_role.id}"
  policy = <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Action": [
        "s3:*"
      ],
      "Effect": "Allow",
      "Resource": [
        "${data.terraform_remote_state.s3_buckets.apps_s3_bucket_arn}",
        "${data.terraform_remote_state.s3_buckets.apps_s3_bucket_arn}/*",
        "${data.terraform_remote_state.s3_buckets.stack_s3_bucket_arn}",
        "${data.terraform_remote_state.s3_buckets.stack_s3_bucket_arn}/*"
      ]
    }
  ]
}
EOF
}

resource "aws_iam_instance_profile" "slave_instance_profile" {
  count = "${local.multi_iam_role}"
  name  = "${var.tag_owner}-${var.environment}-slave_role"
  role = "${aws_iam_role.slave_role.name}"
}

resource "aws_iam_role" "captain_role" {
  count = "${local.multi_iam_role}"
  name = "${var.tag_owner}-${var.environment}-captain_role"
  path = "/"
  assume_role_policy = "${data.template_file.assume_role_policy.rendered}"
}

resource "aws_iam_role_policy_attachment" "captain_ssm_attach" {
  count      = "${local.include_ssm * local.multi_iam_role}"
  role       = "${aws_iam_role.captain_role.name}"
  policy_arn = "arn:${var.arn}:iam::aws:policy/service-role/AmazonEC2RoleforSSM"
}

resource "aws_iam_role_policy" "captain_policy" {
  count = "${local.multi_iam_role}"
  name = "${var.tag_owner}-${var.environment}-captain_policy"
  role = "${aws_iam_role.captain_role.id}"
  policy = <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Action": [
        "s3:*"
      ],
      "Effect": "Allow",
      "Resource": [
        "${data.terraform_remote_state.s3_buckets.apps_s3_bucket_arn}",
        "${data.terraform_remote_state.s3_buckets.apps_s3_bucket_arn}/*",
        "${data.terraform_remote_state.s3_buckets.stack_s3_bucket_arn}",
        "${data.terraform_remote_state.s3_buckets.stack_s3_bucket_arn}/*"
      ]
    },
    {
      "Action": [
        "ec2:DescribeInstances"
      ],
      "Effect": "Allow",
      "Resource": "*"
    },
    {
      "Action": [
        "ec2:*"
      ],
      "Effect": "Allow",
      "Resource": "*",
      "Condition": {
        "StringEquals": {
            "ec2:ResourceTag/owner": "knoldususa"
        }
      }
    }
  ]
}
EOF
}

resource "aws_iam_instance_profile" "captain_instance_profile" {
  count = "${local.multi_iam_role}"
  name  = "${var.tag_owner}-${var.environment}-captain_role"
  role = "${aws_iam_role.captain_role.name}"
}

# Extra policy for ssh keys bucket if applicable

resource "aws_iam_policy" "ssh_key_bucket" {
  count = "${local.create_extra_ssh_key_policy}"

  name = "${var.tag_owner}-${var.environment}-ssh_key_bucket"
  policy = <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Action": [
        "s3:GetObject"
      ],
      "Effect": "Allow",
      "Resource": [
        "arn:${var.arn}:s3:::${var.ssh_keys_s3_bucket}"
      ]
    }
  ]
}
EOF
}

resource "aws_iam_policy_attachment" "ssh_key_bucket_attach" {
  count      = "${local.create_extra_ssh_key_policy * local.multi_iam_role}"

  name       = "${aws_iam_policy.ssh_key_bucket.name}"
  roles      = ["${aws_iam_role.bastion_role.name}", "${aws_iam_role.bootstrap_role.name}", "${aws_iam_role.master_role.name}", "${aws_iam_role.slave_role.name}", "${aws_iam_role.captain_role.name}"]
  policy_arn = "${aws_iam_policy.ssh_key_bucket.arn}"
}

resource "aws_iam_policy" "artifacts_bucket" {
  count = "${local.create_artifacts_bucket_policy}"

  name = "${var.tag_owner}-${var.environment}-artifacts_bucket"
  policy = <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Action": [
        "s3:*"
      ],
      "Effect": "Allow",
      "Resource": [
        "arn:${var.arn}:s3:::${var.artifacts_s3_bucket}",
        "arn:${var.arn}:s3:::${var.artifacts_s3_bucket}/*"
      ]
    }
  ]
}
EOF
}

resource "aws_iam_policy_attachment" "artifacts_bucket_attach" {
  count      = "${local.create_artifacts_bucket_policy * local.multi_iam_role}"

  name       = "${aws_iam_policy.artifacts_bucket.name}"
  roles      = ["${aws_iam_role.bastion_role.name}", "${aws_iam_role.bootstrap_role.name}", "${aws_iam_role.master_role.name}", "${aws_iam_role.slave_role.name}", "${aws_iam_role.captain_role.name}"]
  policy_arn = "${aws_iam_policy.artifacts_bucket.arn}"
}

# Singular IAM Role

resource "aws_iam_role" "deepcortex_main_role" {
  count = "${local.singular_iam_role}"
  name = "${var.tag_owner}-${var.environment}-knoldususa_main_role"
  path = "/"
  max_session_duration = "43200"
  assume_role_policy = "${data.template_file.assume_role_policy.rendered}"
}

# IAM Role for creating temporary credential

resource "aws_iam_role" "deepcortex_jump_role" {
  name = "${var.tag_owner}-${var.environment}-knoldususa_jump_role"
  path = "/"
  max_session_duration = "43200"
  assume_role_policy =<<EOF
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Action": "sts:AssumeRole",
            "Principal": {
               "AWS": "${aws_iam_role.deepcortex_main_role.arn}"
            },
            "Effect": "Allow"
        }
    ]
}
EOF
}

resource "aws_iam_role_policy" "deepcortex_jump_policy" {
  name = "${var.tag_owner}-${var.environment}-knoldususa_jump_policy"
  role = "${aws_iam_role.deepcortex_jump_role.id}"
  policy = <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Action": ["s3:*"],
      "Effect": "Allow",
      "Resource": [
        "${data.terraform_remote_state.s3_buckets.apps_s3_bucket_arn}",
        "${data.terraform_remote_state.s3_buckets.apps_s3_bucket_arn}/*"
      ]
    }
  ]
}
EOF
}

resource "aws_iam_role_policy_attachment" "deepcortex_main_ssm_attach" {
  count      = "${local.include_ssm * local.singular_iam_role}"
  role       = "${aws_iam_role.deepcortex_main_role.name}"
  policy_arn = "arn:${var.arn}:iam::aws:policy/service-role/AmazonEC2RoleforSSM"
}

resource "aws_iam_role_policy" "deepcortex_main_policy" {
  count = "${local.singular_iam_role}"
  name = "${var.tag_owner}-${var.environment}-knoldususa_main_policy"
  role = "${aws_iam_role.deepcortex_main_role.id}"
  policy = <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Action": [
        "s3:*"
      ],
      "Effect": "Allow",
      "Resource": [
        "${data.terraform_remote_state.s3_buckets.apps_s3_bucket_arn}",
        "${data.terraform_remote_state.s3_buckets.apps_s3_bucket_arn}/*",
        "${data.terraform_remote_state.s3_buckets.stack_s3_bucket_arn}",
        "${data.terraform_remote_state.s3_buckets.stack_s3_bucket_arn}/*"
      ]
    },
    {
      "Action": [
        "ec2:DescribeInstances"
      ],
      "Effect": "Allow",
      "Resource": "*"
    },
    {
      "Action": [
        "ec2:*"
      ],
      "Effect": "Allow",
      "Resource": "*",
      "Condition": {
        "StringEquals": {
            "ec2:ResourceTag/owner": "knoldususa"
        }
      }
    }
  ]
}
EOF
}

resource "aws_iam_instance_profile" "deepcortex_main_instance_profile" {
  count = "${local.singular_iam_role}"
  name  = "${var.tag_owner}-${var.environment}-knoldususa_main_role"
  role = "${aws_iam_role.deepcortex_main_role.name}"
}

resource "aws_iam_policy_attachment" "singular_ssh_key_bucket_attach" {
  count      = "${local.create_extra_ssh_key_policy * local.singular_iam_role}"

  name       = "${aws_iam_policy.ssh_key_bucket.name}"
  roles      = ["${aws_iam_role.deepcortex_main_role.name}"]
  policy_arn = "${aws_iam_policy.ssh_key_bucket.arn}"
}

resource "aws_iam_policy_attachment" "singular_artifacts_bucket_attach" {
  count      = "${local.create_artifacts_bucket_policy * local.singular_iam_role}"

  name       = "${aws_iam_policy.artifacts_bucket.name}"
  roles      = ["${aws_iam_role.deepcortex_main_role.name}"]
  policy_arn = "${aws_iam_policy.artifacts_bucket.arn}"
}
