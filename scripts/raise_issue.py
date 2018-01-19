#!/usr/bin/env python

"""
Raises an issue with the specified options.

Usage example:

./raise-issue.py -t "Issue name" -f file -a zaproxy -p changeme

"""
import argparse
import json
import requests

def make_github_issue(title, owner, repo, user, password, body=None, assignee=None, milestone=None, labels=None):
    '''Create an issue on github.com using the given parameters.'''
    # url to create issues via POST
    url = 'https://api.github.com/repos/%s/%s/issues' % (owner, repo)
    # Create an authenticated session to create the issue
    session = requests.Session()
    session.auth = (user, password)
    # Create our issue
    issue = {'title': title,
             'body': body,
             'assignee': assignee}
        #     'milestone': milestone,
        #     'labels': labels}
    # Add the issue to our repository
    r = session.post(url, json.dumps(issue))
    if r.status_code == 201:
        print 'Successfully created Issue "%s"' % title
    else:
        print 'Could not create Issue "%s"' % title
        print 'Response:', r.content
    return r.content


def get_args():
    parser = argparse.ArgumentParser(description=__doc__)

    parser.add_argument('-t', '--title',
                        required=True,
                        help='Issue title')

    parser.add_argument('-f', '--file',
                        required=True,
                        help='File containing the issue body')

    parser.add_argument('-o', '--owner',
			default='zaproxy',
                        help='Repo owner, e.g. zaproxy')

    parser.add_argument('-r', '--repo',
			default='zap-admin',
                        help='Repo, e.g. zap-admin')

    parser.add_argument('-u', '--user',
			default='zapbot',
                        help='User, e.g. zapbot')

    parser.add_argument('-p', '--password',
                        required=True,
                        help='Password / credentials')

    parser.add_argument('-a', '--assignee',
                        required=False,
                        help='Assigne, e.g. zapbot')

    parsed_args = parser.parse_args()
    return vars(parsed_args)

def main():
    cli_args = get_args()

    with open(cli_args['file'], 'r') as content_file:
      content = content_file.read()

    return make_github_issue(cli_args['title'], cli_args['owner'], cli_args['repo'], cli_args['user'], 
        cli_args['password'], body=content, assignee=cli_args['assignee'])


if __name__ == '__main__':
    main()
