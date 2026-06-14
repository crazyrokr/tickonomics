"""Test harness for the forecast orchestrator Lambda handlers.

The handlers import ``boto3`` and build module-level clients, but the AWS SDK is
not installed in this environment. This conftest installs a stdlib-only fake
``boto3`` into ``sys.modules`` before the handlers are imported, so each handler
module binds its module-level ``ec2``/``s3`` clients to the shared singletons
below. Tests configure the singletons and assert on the recorded calls.
"""

import os
import sys

SRC_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "src"))
if SRC_DIR not in sys.path:
    sys.path.insert(0, SRC_DIR)


class FakeClientError(Exception):
    """Mimics botocore.exceptions.ClientError for the handlers' except clauses."""

    def __init__(self, code, message=""):
        super().__init__(f"{code}: {message}")
        self.response = {"Error": {"Code": code, "Message": message}}


class _BodyStream:
    def __init__(self, data):
        self._data = data if isinstance(data, bytes) else data.encode("utf-8")

    def read(self):
        return self._data


class _Exceptions:
    ClientError = FakeClientError


class FakeEc2:
    def __init__(self):
        self.run_instances_calls = []
        self.run_instances_response = {"Instances": [{"InstanceId": "i-testinstance"}]}
        self.run_instances_error = None

    def run_instances(self, **kwargs):
        self.run_instances_calls.append(kwargs)
        if self.run_instances_error is not None:
            raise self.run_instances_error
        return self.run_instances_response


class FakeS3:
    def __init__(self):
        self.objects = {}
        self.put_calls = []
        self.exceptions = _Exceptions()

    def head_object(self, Bucket, Key):
        if Key not in self.objects:
            raise FakeClientError("404")
        return {}

    def get_object(self, Bucket, Key):
        if Key not in self.objects:
            raise FakeClientError("NoSuchKey")
        return {"Body": _BodyStream(self.objects[Key])}

    def put_object(self, Bucket, Key, Body, **kwargs):
        self.objects[Key] = Body if isinstance(Body, bytes) else Body.encode("utf-8")
        self.put_calls.append({"Bucket": Bucket, "Key": Key, "Body": Body})
        return {}


_FAKE_EC2 = FakeEc2()
_FAKE_S3 = FakeS3()


class FakeBoto3:
    @staticmethod
    def client(name):
        if name == "ec2":
            return _FAKE_EC2
        if name == "s3":
            return _FAKE_S3
        raise ValueError(f"unexpected client: {name}")

    @staticmethod
    def setup_default_session(*args, **kwargs):
        return None


sys.modules["boto3"] = FakeBoto3


def reset_fakes():
    _FAKE_EC2.run_instances_calls.clear()
    _FAKE_EC2.run_instances_response = {"Instances": [{"InstanceId": "i-testinstance"}]}
    _FAKE_EC2.run_instances_error = None
    _FAKE_S3.objects.clear()
    _FAKE_S3.put_calls.clear()


def fake_ec2():
    return _FAKE_EC2


def fake_s3():
    return _FAKE_S3
