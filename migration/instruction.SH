# STEPS TO DEPLOY THE CORTEX API REST TO EKS

1 - kubectl apply -f . -n cortex-rest  # This will deploy all services in eks

2- kubectl get ing -n cortex-rest -w

output -> cortex-api.s9.devopscloud.link = listening on this

# steps for corerecting auth for eks.

- export KUBE_EDITOR='code -w'
- kubectl edit cm -n kube-system aws-auth

paste the below data:
 mapUsers: |
    - userarn: arn:aws:iam::086351999451:root # from aws sts get-caller-identity
      username: admin
      groups:
        - system:masters