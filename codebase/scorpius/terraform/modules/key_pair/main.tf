# vim: ts=4:sw=4:et:ft=hcl

###############################################################################

resource "aws_key_pair" "key" {
    key_name   = "${var.key_name}"
    public_key = "${var.public_key}"
}

